package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.S3StorageService;
import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    @Value("${ingestion.embedding-batch-size:20}")
    private int embeddingBatchSize;

    private final FileCloneService fileCloneService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final EmbeddingRetryPolicy retryPolicy;
    private final FullIngestionStore store;
    private final IngestionHeartbeat heartbeat;
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

        var claimed = store.claim(repoId, ingestionLeaseSeconds);
        if (claimed.isEmpty()) return;
        RepoEntity repo = claimed.get();
        UUID owner = repo.getIngestionOwner();
        try (var renewal = heartbeat.start(repoId, owner, ingestionLeaseSeconds)) {
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
                throw new NoProcessableFiles(
                        "No processable code files found. Check if the repo has supported file types."
                );
            }

            repo.setTotalFiles(files.size());
            store.saveProgress(repo, owner);
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
                store.saveProgress(repo, owner);
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
                        store.saveProgress(repo, owner);
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
            store.saveProgress(repo, owner);
            log.info("Ingestion complete: {} files, {} chunks for repo {}",
                    processedCount, chunkCount, repoId);
        } catch (FullIngestionStore.OwnershipLost e) {
            throw e;
        } catch (NoProcessableFiles e) {
            // This known input outcome will not improve by repeating the same job.
            repo.setStatus(RepoEntity.IngestionStatus.FAILED);
            repo.setErrorMessage(e.getMessage());
            repo.setIngestionLeaseUntil(null);
            repo.setSyncMessage(null);
            store.saveProgress(repo, owner);
        } catch (Exception e) {
            // Infrastructure/provider failures and unknown errors must escape:
            // a normal return authorizes SQS deletion. The heartbeat has stopped
            // before ownership is released, so it cannot revive this attempt.
            log.error("Ingestion attempt failed for repo {}. Retaining job for retry.", repoId, e);
            try {
                store.releaseForRetry(repoId, owner, e.getMessage());
            } catch (RuntimeException releaseFailure) {
                // A database outage can also prevent release. Preserve delivery;
                // lease expiry permits recovery, and stale owners cannot mutate it.
                e.addSuppressed(releaseFailure);
                log.warn("Could not release ingestion lease for {}; awaiting expiry", repoId, releaseFailure);
            }
            throw new IllegalStateException("Ingestion attempt failed; retained for retry", e);
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
        List<float[]> vectors;
        try {
            vectors = embedBatchWithRetry(batch);
            if (vectors.size() != batch.size()) throw new IllegalStateException("Embedding count mismatch");
        } catch (NonTransientAiException | EmbeddingRetryPolicy.RetryExhaustedException e) {
            // Authentication/configuration errors need intervention, and an exhausted rate limit or outage must not be
            // answered with one call per chunk. Preserve the queue job for bounded redelivery/DLQ handling.
            throw e;
        } catch (RuntimeException e) {
            log.warn("Retrying {} failed batch embeddings individually", batch.size(), e);
            vectors = new ArrayList<>(batch.size());
            for (var item : batch) vectors.add(embedSingleWithRetry(item.chunk().content()));
        }
        // A failed DB write must fail the job. Retrying the whole batch here could duplicate earlier inserts.
        List<CodeChunk> entities = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) entities.add(toEntity(repo, batch.get(i), vectors.get(i)));
        store.appendBatch(repo.getId(), repo.getIngestionOwner(), entities);
        return batch.size();
    }

    private List<float[]> embedBatchWithRetry(List<PendingChunk> chunks) {
        List<String> texts = chunks.stream().map(item -> item.chunk().content()).toList();
        return retryPolicy.execute(() -> embeddingService.embedBatch(texts));
    }

    private float[] embedSingleWithRetry(String text) {
        return retryPolicy.execute(() -> embeddingService.embed(text));
    }

    private CodeChunk toEntity(RepoEntity repo, PendingChunk pendingChunk, float[] embedding) {
        return CodeChunk.builder()
                .repository(repo)
                .filePath(pendingChunk.chunk().filePath())
                .language(toLanguage(pendingChunk.extension()))
                .content(pendingChunk.chunk().content())
                .chunkIndex(pendingChunk.chunk().chunkIndex())
                .startLine(pendingChunk.chunk().startLine())
                .endLine(pendingChunk.chunk().endLine())
                .embedding(embedding)
                .build();

    }

    private record PendingChunk(String extension, ChunkingService.Chunk chunk) {}

    private static class NoProcessableFiles extends RuntimeException {
        NoProcessableFiles(String message) { super(message); }
    }
}
