package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.queue.IngestionQueuePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    @Value("${ingestion.embedding-batch-size:20}")
    private int embeddingBatchSize;

    @Value("${ingestion.max-embed-retries:5}")
    private int maxEmbedRetries;

    private final GitHubApiService gitHubApiService;
    private final FileCloneService fileCloneService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;
    // Fallback path only. A full re-ingest is exactly as slow/expensive as the
    // original /ingest call, so it goes through the same durable SQS queue
    // rather than a direct in-process call — a crashed instance mid-fallback
    // must not silently lose the retry the way a plain @Async call would.
    private final IngestionQueuePublisher ingestionQueuePublisher;

    @Async
    public void syncAsync(UUID repoId, String token) {
        RepoEntity repo = null;
        boolean persistInFinally = true;

        try {
            repo = repoRepository.findById(repoId)
                    .orElseThrow(() -> new RuntimeException("Repo not found: " + repoId));

            // The controller claimed syncing=true atomically before it
            // dispatched this worker.  Keep this early fallback inside the
            // try/finally so the claim is released even when SQS full
            // ingestion takes over.
            if (repo.getLastCommitSha() == null) {
                log.info("Repo {} has no baseline commit — falling back to full ingest", repoId);
                // fallbackToFullReingest persists PENDING/FAILED itself and
                // enqueues SQS work. Do not later merge this stale detached
                // entity over progress written by that worker.
                persistInFinally = false;
                fallbackToFullReingest(
                        repo,
                        token,
                        "This repository needs a full re-index before sync can continue. It may take a few minutes."
                );
                return;
            }

            GitHubApiService.RepoCoordinates coords = gitHubApiService.parse(repo.getGithubUrl());
            String latestSha = gitHubApiService.getLatestCommitSha(coords, token);

            if (latestSha.equals(repo.getLastCommitSha())) {
                log.info("Repo {} already at latest commit {}", repoId, latestSha);
                repo.setLastSyncedAt(LocalDateTime.now());
                repo.setSyncMessage(null);
                return; // zero clone, zero embeddings — nothing changed
            }

            GitHubApiService.CompareResult diff =
                    gitHubApiService.compare(coords, repo.getLastCommitSha(), latestSha, token);

            // Either signal means the file-level diff can't be trusted as a
            // complete picture of what changed since repo.getLastCommitSha():
            // tooLargeToDiff() also covers a 404 (base commit fully gone),
            // and diverged() covers the trickier case where the base commit
            // is still reachable but history was rewritten around it — see
            // GitHubApiService.compare for why that diff isn't safe to apply
            // incrementally.
            if (diff.tooLargeToDiff() || diff.diverged()) {
                log.warn("Diff for repo {} is not a trustworthy incremental diff " +
                                "(tooLarge={}, diverged={}) — falling back to full re-ingest",
                        repoId, diff.tooLargeToDiff(), diff.diverged());
                // See the no-baseline fallback above: the full-ingestion
                // worker must be the only writer after it is queued.
                persistInFinally = false;
                fallbackToFullReingest(
                        repo,
                        token,
                        "Large or rewritten repository changes detected. RepoMind will run a full re-index, which may take a few minutes."
                );
                return;
            }

            int filesTouched = 0;
            List<PendingChunk> pendingChunks = new ArrayList<>();
            Map<String, FileSyncState> fileStates = new HashMap<>();
            repo.setSyncMessage("Incremental sync is running. This should finish shortly.");
            repoRepository.save(repo);
            for (GitHubApiService.ChangedFile cf : diff.files()) {
                switch (cf.status()) {
                    case "removed" ->
                            chunkRepository.deleteByRepositoryIdAndFilePath(repoId, cf.filename());
                    case "renamed" -> {
                        queueFileChunks(repo, coords, cf.filename(), cf.previousFilename(), cf.blobSha(), token,
                                pendingChunks, fileStates);
                    }
                    default ->
                            queueFileChunks(repo, coords, cf.filename(), null, cf.blobSha(), token,
                                    pendingChunks, fileStates);
                }
                flushPendingChunks(repo, pendingChunks, fileStates);
                filesTouched++;
            }

            flushPendingChunks(repo, pendingChunks, fileStates);

            repo.setLastCommitSha(latestSha);
            repo.setLastSyncedAt(LocalDateTime.now());
            repo.setTotalChunks((int) chunkRepository.countByRepositoryId(repoId));
            repo.setSyncMessage(null);
            cacheService.evictUserReposCache();

            log.info("Sync complete for repo {}: {} files touched ({} -> {})",
                    repoId, filesTouched, repo.getLastCommitSha(), latestSha);

        } catch (Exception e) {
            log.error("Sync failed for repo {}: {}", repoId, e.getMessage(), e);
            // If fallback itself failed before it persisted its new state,
            // save the error below rather than leaving the previous READY row
            // marked as syncing.
            if (repo != null) {
                repo.setErrorMessage("Sync failed: " + e.getMessage());
                repo.setSyncMessage("Sync could not complete. Please try again.");
                persistInFinally = true;
            }
        } finally {
            // Persist normal-result fields (commit SHA, totals, timestamps or
            // an error) first.  Release only the sync flag with a bulk update
            // so this cleanup cannot overwrite those fields.
            try {
                if (repo != null && persistInFinally) {
                    repoRepository.save(repo);
                }
            } finally {
                repoRepository.releaseIncrementalSync(repoId);
            }
        }
    }

    private void queueFileChunks(RepoEntity repo, GitHubApiService.RepoCoordinates coords,
                                 String filePath, String previousFilePath, String blobSha, String token,
                                 List<PendingChunk> pendingChunks, Map<String, FileSyncState> fileStates) {
        List<UUID> staleChunkIds = new ArrayList<>(chunkRepository.findIdsByRepositoryIdAndFilePath(repo.getId(), filePath));
        if (previousFilePath != null) {
            staleChunkIds.addAll(chunkRepository.findIdsByRepositoryIdAndFilePath(repo.getId(), previousFilePath));
        }
        try {
            String content = gitHubApiService.getBlobContent(coords, blobSha, token);
            if (content == null || content.isBlank()) {
                deleteStale(staleChunkIds);
                return;
            }

            long sizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
            if (!fileCloneService.isIngestable(filePath, sizeBytes)) {
                deleteStale(staleChunkIds);
                return;
            }

            String ext = fileCloneService.extensionOf(filePath);
            List<ChunkingService.Chunk> chunks = chunkingService.chunkFile(filePath, content);
            if (chunks.isEmpty()) {
                deleteStale(staleChunkIds);
                return;
            }

            String stateKey = filePath;
            fileStates.put(stateKey, new FileSyncState(staleChunkIds, chunks.size()));
            for (ChunkingService.Chunk chunk : chunks) {
                pendingChunks.add(new PendingChunk(stateKey, ext, chunk));
            }

        } catch (Exception e) {
            log.warn("Could not re-embed {} — keeping previous version: {}", filePath, e.getMessage());
        }
    }

    private void flushPendingChunks(RepoEntity repo, List<PendingChunk> pendingChunks, Map<String, FileSyncState> fileStates)
            throws InterruptedException {
        while (pendingChunks.size() >= embeddingBatchSize) {
            List<PendingChunk> batch = new ArrayList<>(pendingChunks.subList(0, embeddingBatchSize));
            try {
                List<float[]> embeddings = embedBatchWithRetry(batch);
                for (int i = 0; i < batch.size(); i++) {
                    saveChunk(repo, batch.get(i), embeddings.get(i), fileStates);
                }
            } catch (Exception batchException) {
                log.warn("Batch embedding failed for {} chunks. Falling back to per-chunk embeddings: {}",
                        batch.size(), batchException.getMessage());
                for (PendingChunk item : batch) {
                    try {
                        float[] embedding = embedSingleWithRetry(item.chunk().content());
                        saveChunk(repo, item, embedding, fileStates);
                    } catch (Exception chunkException) {
                        log.warn("Skipping chunk {} in {}: {}",
                                item.chunk().chunkIndex(), item.chunk().filePath(), chunkException.getMessage());
                    }
                }
            }
            pendingChunks.subList(0, embeddingBatchSize).clear();
        }
    }

    private List<float[]> embedBatchWithRetry(List<PendingChunk> chunks) throws InterruptedException {
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
                Thread.sleep(delay);
                log.warn("Rate limit hit for batch. Retrying in {} ms...", delay);
                delay *= 2;
            }
        }
        throw new IllegalStateException("Batch embedding retry loop exited unexpectedly");
    }

    private float[] embedSingleWithRetry(String text) throws InterruptedException {
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
                Thread.sleep(delay);
                log.warn("Rate limit hit. Retrying in {} ms...", delay);
                delay *= 2;
            }
        }
        throw new IllegalStateException("Embedding retry loop exited unexpectedly");
    }

    private void saveChunk(RepoEntity repo, PendingChunk pendingChunk, float[] embedding, Map<String, FileSyncState> fileStates) {
        chunkRepository.save(CodeChunk.builder()
                .repository(repo)
                .filePath(pendingChunk.chunk().filePath())
                .language(IngestionService.toLanguage(pendingChunk.extension()))
                .content(pendingChunk.chunk().content())
                .chunkIndex(pendingChunk.chunk().chunkIndex())
                .startLine(pendingChunk.chunk().startLine())
                .endLine(pendingChunk.chunk().endLine())
                .embedding(embedding)
                .build());

        FileSyncState state = fileStates.get(pendingChunk.filePathKey());
        if (state != null) {
            state.savedChunks++;
            if (state.savedChunks >= state.totalChunks && !state.staleDeleted) {
                deleteStale(state.staleChunkIds);
                state.staleDeleted = true;
            }
        }
    }

    private void deleteStale(List<UUID> ids) {
        if (!ids.isEmpty()) chunkRepository.deleteByIdIn(ids);
    }

    private record PendingChunk(String filePathKey, String extension, ChunkingService.Chunk chunk) {}

    private static final class FileSyncState {
        private final List<UUID> staleChunkIds;
        private final int totalChunks;
        private int savedChunks;
        private boolean staleDeleted;

        private FileSyncState(List<UUID> staleChunkIds, int totalChunks) {
            this.staleChunkIds = staleChunkIds;
            this.totalChunks = totalChunks;
        }
    }

    private void fallbackToFullReingest(RepoEntity repo, String token, String syncMessage) {
        chunkRepository.deleteByRepositoryId(repo.getId());
        repo.setStatus(RepoEntity.IngestionStatus.PENDING);
        repo.setProcessedFiles(0);
        repo.setTotalChunks(0);
        repo.setSyncMessage(syncMessage);
        repoRepository.save(repo);
        try {
            ingestionQueuePublisher.enqueue(repo.getId(), repo.getGithubUrl(), token);
        } catch (Exception e) {
            // Mirror IngestionController's own contract: never leave a repo
            // claiming to be PENDING when no durable job actually exists for it.
            log.error("Could not enqueue fallback re-ingest for repo {}", repo.getId(), e);
            repo.setStatus(RepoEntity.IngestionStatus.FAILED);
            repo.setErrorMessage("Could not queue re-ingestion job. Please try again.");
            repo.setSyncMessage(null);
            repoRepository.save(repo);
        }
    }
}
