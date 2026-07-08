package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.controller.CacheService;
import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final FileCloneService fileCloneService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;
    // @Async: Spring picks a thread from a thread pool and runs this method there
    // The thread that handled your HTTP request is freed immediately
    // The client gets the 202 response and starts polling /status
    // Meanwhile this method runs in the background for 3-10 minutes
    //
    // IMPORTANT: @Async only works if the method is called from a DIFFERENT class
    // If IngestionController called ingestAsync from THIS class directly via this.ingestAsync()
    // the @Async would be ignored — Spring's proxy cannot intercept internal calls
    // It works because IngestionController injects IngestionService via Spring
    // and calls it through Spring's proxy, which intercepts and runs it async

    @Async
    public void ingestAsync(UUID repoId,String githubUrl, String token){
        Path tempDir = null;

        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() ->new RuntimeException("Repo not found: " + repoId));
        try{
            //1.Mark as Processsing
            repo.setStatus(RepoEntity.IngestionStatus.PROCESSING);
            repoRepository.save(repo);

            //2. Clone the repository
            // FileCloneService.cloneRepository downloads the repo to a temp folder
            // Returns the path to that folder
            tempDir = fileCloneService.cloneRepository(githubUrl,token);

            // ── 3. Extract all code files ───────────────────────────────────
            // FileCloneService.extractFiles walks the folder, skips junk,
            // returns a list of ParsedFile records (relativePath, content, extension)

            List<FileCloneService.ParsedFile> files = fileCloneService.extractFiles(tempDir);

            if (files.isEmpty()) {
                throw new RuntimeException(
                        "No processable code files found. Check if the repo has supported file types."
                );
            }

            repo.setTotalFiles(files.size());
            repoRepository.save(repo);
            log.info("Found {} files to process for repo {}", files.size(), repoId);

            // ── 4. Process each file ─────────────────────────────────────────
            int processedCount = 0;
            int chunkCount = 0;

            for(FileCloneService.ParsedFile file : files) {
                try {
                    List<ChunkingService.Chunk> chunks = chunkingService.chunkFile(
                            file.relativePath(),
                            file.content()
                    );

                    for (ChunkingService.Chunk chunk : chunks) {
                        // EmbeddingService converts chunk text → float[768]
                        // This calls Nomic API — one HTTP call per chunk
                        // A 200-file repo might create 400-600 chunks total
                        float[] embedding = embeddingService.embed(chunk.content());


                        CodeChunk entity = CodeChunk.builder()
                                .repository(repo)
                                .filePath(chunk.filePath())
                                .language(toLanguage(file.extension()))
                                .content(chunk.content())
                                .chunkIndex(chunk.chunkIndex())
                                .startLine(chunk.startLine())
                                .endLine(chunk.endLine())
                                .embedding(embedding)
                                .build();

                        chunkRepository.save(entity);
                        chunkCount++;
                    }
                    processedCount++;
                    // Update progress every 5 files so the frontend progress
                    // bar moves visibly — updating every file would be too many DB writes
                    if (processedCount % 5 == 0) {
                        repo.setProcessedFiles(processedCount);
                        repo.setTotalChunks(chunkCount);
                        repoRepository.save(repo);
                        log.info("Progress: {}/{} files, {} chunks",
                                processedCount, files.size(), chunkCount);
                    }
                }catch (Exception e)
                {
                    // One unreadable file (encoding issues, binary disguised as text)
                    // should NOT stop the entire ingestion
                    // Log it and move on to the next file
                    log.warn("Skipping file {} due to error: {}",
                            file.relativePath(), e.getMessage());
                }
            }
            // ── 5. Mark as READY ─────────────────────────────────────────────
            repo.setStatus(RepoEntity.IngestionStatus.READY);
            cacheService.evictUserReposCache();
            repo.setProcessedFiles(processedCount);
            repo.setTotalChunks(chunkCount);
            repoRepository.save(repo);
            log.info("Ingestion complete: {} files, {} chunks for repo {}",
                    processedCount, chunkCount, repoId);
        } catch (Exception e) {
            // Top-level failure: clone failed, no files found, DB connection issue
            log.error("Ingestion failed for repo {}: {}", repoId, e.getMessage(), e);
            repo.setStatus(RepoEntity.IngestionStatus.FAILED);
            cacheService.evictUserReposCache();
            repo.setErrorMessage(e.getMessage());
            repoRepository.save(repo);
            throw new RuntimeException(e);
        } finally {
            // finally block runs whether ingestion succeeded or failed
            // Always delete the temp directory — never leave cloned repos on disk
            // A failed ingestion on a large repo could leave gigabytes behind
            if (tempDir != null) {
                fileCloneService.deleteDirectory(tempDir);
                log.info("Cleaned up temp directory for repo {}", repoId);
            }
        }
    }
    private String toLanguage(String extension) {
        return switch (extension) {
            case ".java" -> "java";
            case ".js", ".jsx" -> "javascript";
            case ".ts", ".tsx" -> "typescript";
            case ".py" -> "python";
            case ".go" -> "go";
            case ".rs" -> "rust";
            case ".kt" -> "kotlin";
            case ".rb" -> "ruby";
            case ".cs" -> "csharp";
            case ".php" -> "php";
            case ".md" -> "markdown";
            case ".sql" -> "sql";
            case ".yml", ".yaml" -> "yaml";
            case ".json" -> "json";
            case ".xml" -> "xml";
            case ".html" -> "html";
            case ".css" -> "css";
            case ".sh" -> "shell";
            default -> "text";
        };
    }
}
