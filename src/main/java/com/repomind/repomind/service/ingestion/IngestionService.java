package com.repomind.repomind.service.ingestion;

import com.openai.errors.RateLimitException;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
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

    @Async
    public void ingestAsync(UUID repoId,String githubUrl, String token){
        Path tempDir = null;
        String headSha = null;

        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() ->new RuntimeException("Repo not found: " + repoId));
        try{
            repo.setStatus(RepoEntity.IngestionStatus.PROCESSING);
            repoRepository.save(repo);

            tempDir = fileCloneService.cloneRepository(githubUrl,token);
            // Capture the baseline commit now, while the clone still exists —
            // this is what future /sync calls diff against.
            headSha = fileCloneService.getHeadCommitSha(tempDir);

            List<FileCloneService.ParsedFile> files = fileCloneService.extractFiles(tempDir);

            if (files.isEmpty()) {
                throw new RuntimeException(
                        "No processable code files found. Check if the repo has supported file types."
                );
            }

            repo.setTotalFiles(files.size());
            repoRepository.save(repo);
            log.info("Found {} files to process for repo {}", files.size(), repoId);

            int processedCount = 0;
            int chunkCount = 0;
            int maxRetries = 5;
            for(FileCloneService.ParsedFile file : files) {

                List<ChunkingService.Chunk> chunks = chunkingService.chunkFile(
                        file.relativePath(),
                        file.content()
                );

                for (ChunkingService.Chunk chunk : chunks) {
                    try {
                        long delay = 1000;
                        float[] embedding = new float[0];
                        for (int i = 0; i < maxRetries; i++) {
                            try {

                                embedding = embeddingService.embed(chunk.content());
                                log.debug("Embedding done on {} try", i + 1);
                                break;
                            } catch (NonTransientAiException e) {
                                if (i == maxRetries - 1) {
                                    throw e;
                                }
                                Thread.sleep(delay);
                                log.warn("Rate limit hit. Retrying in {} ms...", delay);
                                delay *= 2;
                            }
                        }


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
                    } catch (Exception e) {
                        log.error("Exception class: {}", e.getClass().getName(), e);
                        log.warn(
                                "Skipping chunk {} in file {}: {}",
                                chunk.chunkIndex(),
                                file.relativePath(),
                                e.getMessage()
                        );
                    }
                }
                processedCount++;
                if (processedCount % 5 == 0) {
                    repo.setProcessedFiles(processedCount);
                    repo.setTotalChunks(chunkCount);
                    repoRepository.save(repo);
                    log.info("Progress: {}/{} files, {} chunks",
                            processedCount, files.size(), chunkCount);
                }
            }

            repo.setStatus(RepoEntity.IngestionStatus.READY);
            repo.setLastCommitSha(headSha);
            repo.setLastSyncedAt(LocalDateTime.now());
            cacheService.evictUserReposCache();
            repo.setProcessedFiles(processedCount);
            repo.setTotalChunks(chunkCount);
            repoRepository.save(repo);
            log.info("Ingestion complete: {} files, {} chunks for repo {}",
                    processedCount, chunkCount, repoId);
        } catch (Exception e) {
            log.error("Ingestion failed for repo {}: {}", repoId, e.getMessage(), e);
            repo.setStatus(RepoEntity.IngestionStatus.FAILED);
            cacheService.evictUserReposCache();
            repo.setErrorMessage(e.getMessage());
            repoRepository.save(repo);
            throw new RuntimeException(e);
        } finally {
            if (tempDir != null) {
                fileCloneService.deleteDirectory(tempDir);
                log.info("Cleaned up temp directory for repo {}", repoId);
            }
        }
    }

    public static String toLanguage(String extension) {
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