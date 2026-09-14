package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.S3StorageService;
import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    @Value("${ingestion.embedding-batch-size:20}")
    private int embeddingBatchSize;

    @Value("${ingestion.max-embed-retries:5}")
    private int maxEmbedRetries;

    private final FileCloneService fileCloneService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;
    private final S3StorageService s3StorageService;

    @Value("${aws.sqs.ingestion-lease-seconds:1500}")
    private long ingestionLeaseSeconds;

    // Runs on a worker thread owned by IngestionQueueConsumer. It deliberately
    // has no @Async annotation: SQS owns delivery and the consumer owns the
    // bounded concurrency limit. A normal return means the job reached a
    // terminal database state (READY or FAILED), so the consumer may delete
    // its SQS message. An unexpected infrastructure failure is allowed to
    // escape so the message becomes visible again after the queue visibility
    // timeout.
    public void processIngestionJob(UUID repoId, String githubUrl, String token){
        Path tempDir = null;
        String headSha;

        LocalDateTime now = LocalDateTime.now();
        int claimed = repoRepository.claimIngestionJob(
                repoId,
                RepoEntity.IngestionStatus.PENDING,
                RepoEntity.IngestionStatus.PROCESSING,
                now,
                now.plusSeconds(ingestionLeaseSeconds)
        );

        // A duplicate SQS delivery, an already-completed job, or a job that
        // another worker currently owns is safely acknowledged without doing
        // the expensive clone and embedding work again.
        if (claimed == 0) {
            log.info("Skipping unclaimable ingestion job for repo {}", repoId);
            return;
        }

        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() ->new RuntimeException("Repo not found: " + repoId));
        try{
            cacheService.evictUserReposCache();

            //2. Clone the repository
            // FileCloneService.cloneRepository downloads the repo to a temp folder
            // Returns the path to that folder
            tempDir = fileCloneService.cloneRepository(githubUrl,token);

            // Capture the baseline commit now, while the clone still exists —
            // this is what future /sync calls diff against (see SyncService).
            headSha = fileCloneService.getHeadCommitSha(tempDir);

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

            // ── 3b. Archive the extracted source to S3 ──────────────────────
            // Zips exactly what we're about to index and uploads it to S3
            // BEFORE we delete the temp clone directory. This means a repo
            // can be re-embedded later (new chunking strategy, new embedding
            // model) without re-cloning from GitHub, and gives users a
            // downloadable snapshot of what RepoMind actually read.
            // Best-effort only: an S3 problem here must never fail ingestion.
            String archiveKey = s3StorageService.uploadRepoArchive(repoId, files);
            if (archiveKey != null) {
                repo.setArchiveKey(archiveKey);
                repoRepository.save(repo);
            }

            // ── 4. Process each file ─────────────────────────────────────────
            int processedCount = 0;
            int chunkCount = 0;
            List<PendingChunk> pendingChunks = new ArrayList<>();
            for(FileCloneService.ParsedFile file : files) {

                    List<ChunkingService.Chunk> chunks = chunkingService.chunkFile(
                            file.relativePath(),
                            file.content()
                    );

                    enqueueChunks(pendingChunks, file.extension(), chunks);
                    while (pendingChunks.size() >= embeddingBatchSize) {
                        List<PendingChunk> batch = new ArrayList<>(pendingChunks.subList(0, embeddingBatchSize));
                        chunkCount += flushPendingChunks(repo, batch);
                        pendingChunks.subList(0, embeddingBatchSize).clear();
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
                }

            if (!pendingChunks.isEmpty()) {
                chunkCount += flushPendingChunks(repo, new ArrayList<>(pendingChunks));
            }

            // ── 5. Mark as READY ────────��───────────────────────────────────
            repo.setStatus(RepoEntity.IngestionStatus.READY);
            // Baseline for /sync: the commit this ingestion actually indexed.
            repo.setLastCommitSha(headSha);
            repo.setLastSyncedAt(LocalDateTime.now());
            cacheService.evictUserReposCache();
            repo.setProcessedFiles(processedCount);
            repo.setTotalChunks(chunkCount);
            repo.setIngestionLeaseUntil(null);
            repo.setSyncMessage(null);
            repoRepository.save(repo);
            log.info("Ingestion complete: {} files, {} chunks for repo {}",
                    processedCount, chunkCount, repoId);
        } catch (Exception e) {
            // Top-level failure: clone failed, no files found, DB connection issue
            log.error("Ingestion failed for repo {}: {}", repoId, e.getMessage(), e);
            repo.setStatus(RepoEntity.IngestionStatus.FAILED);
            cacheService.evictUserReposCache();
            repo.setErrorMessage(e.getMessage());
            repo.setIngestionLeaseUntil(null);
            repo.setSyncMessage(null);
            repoRepository.save(repo);
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

    // public + static so SyncService can reuse the exact same extension->language
    // mapping when re-embedding a single changed file without a full clone.
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

    private void enqueueChunks(List<PendingChunk> pendingChunks, String extension, List<ChunkingService.Chunk> chunks) {
        for (ChunkingService.Chunk chunk : chunks) {
            pendingChunks.add(new PendingChunk(extension, chunk));
        }
    }

    private int flushPendingChunks(RepoEntity repo, List<PendingChunk> batch) {
        int savedChunks = 0;

        try {
            List<float[]> embeddings = embedBatchWithRetry(batch);
            for (int i = 0; i < batch.size(); i++) {
                saveChunk(repo, batch.get(i), embeddings.get(i));
                savedChunks++;
            }
        } catch (Exception batchException) {
            log.warn("Batch embedding failed for {} chunks. Falling back to per-chunk embeddings: {}",
                    batch.size(), batchException.getMessage());
            for (PendingChunk item : batch) {
                try {
                    float[] embedding = embedSingleWithRetry(item.chunk().content());
                    saveChunk(repo, item, embedding);
                    savedChunks++;
                } catch (Exception chunkException) {
                    log.warn(
                            "Skipping chunk {} in file {}: {}",
                            item.chunk().chunkIndex(),
                            item.chunk().filePath(),
                            chunkException.getMessage()
                    );
                }
            }
        }

        return savedChunks;
    }

    private List<float[]> embedBatchWithRetry(List<PendingChunk> chunks) {
        List<String> texts = chunks.stream().map(item -> item.chunk().content()).toList();
        long delay = 1000;
        for (int i = 0; i < maxEmbedRetries; i++) {
            try {
                List<float[]> embeddings = embeddingService.embedBatch(texts);
                log.debug("Batch embedding done on {} try for {} chunks", i + 1, chunks.size());
                return embeddings;
            } catch (NonTransientAiException e) {
                if (i == maxEmbedRetries - 1) {
                    throw e;
                }
                backoff(delay);
                log.warn("Rate limit hit for batch. Retrying in {} ms...", delay);
                delay *= 2;
            }
        }
        throw new IllegalStateException("Batch embedding retry loop exited unexpectedly");
    }

    private float[] embedSingleWithRetry(String text) {
        long delay = 1000;
        for (int i = 0; i < maxEmbedRetries; i++) {
            try {
                float[] embedding = embeddingService.embed(text);
                log.debug("Embedding done on {} try", i + 1);
                return embedding;
            } catch (NonTransientAiException e) {
                if (i == maxEmbedRetries - 1) {
                    throw e;
                }
                backoff(delay);
                log.warn("Rate limit hit. Retrying in {} ms...", delay);
                delay *= 2;
            }
        }
        throw new IllegalStateException("Embedding retry loop exited unexpectedly");
    }

    private void backoff(long delayMillis) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis));
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Retry backoff interrupted");
        }
    }

    private void saveChunk(RepoEntity repo, PendingChunk pendingChunk, float[] embedding) {
        CodeChunk entity = CodeChunk.builder()
                .repository(repo)
                .filePath(pendingChunk.chunk().filePath())
                .language(toLanguage(pendingChunk.extension()))
                .content(pendingChunk.chunk().content())
                .chunkIndex(pendingChunk.chunk().chunkIndex())
                .startLine(pendingChunk.chunk().startLine())
                .endLine(pendingChunk.chunk().endLine())
                .embedding(embedding)
                .build();

        chunkRepository.save(entity);
    }

    private record PendingChunk(String extension, ChunkingService.Chunk chunk) {}
}
