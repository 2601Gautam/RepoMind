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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    @Value("${ingestion.embedding-batch-size:20}")
    private int embeddingBatchSize;

    private final GitHubApiService gitHubApiService;
    private final FileCloneService fileCloneService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final EmbeddingRetryPolicy retryPolicy;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;
    // Fallback path only. A full re-ingest is exactly as slow/expensive as the
    // original /ingest call, so it goes through the same durable SQS queue
    // rather than a direct in-process call — a crashed instance mid-fallback
    // must not silently lose the retry the way a plain @Async call would.
    private final IngestionQueuePublisher ingestionQueuePublisher;
    private final IndexSnapshotWriter snapshotWriter;

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

            Set<String> affected = new LinkedHashSet<>();
            List<PendingChunk> pending = new ArrayList<>();
            for (var file : diff.files()) {
                affected.add(file.filename());
                if ("renamed".equals(file.status()) && file.previousFilename() != null) affected.add(file.previousFilename());
                if ("removed".equals(file.status()) || !fileCloneService.isIngestable(file.filename(), 0)) continue;
                String content = gitHubApiService.getBlobContent(coords, file.blobSha(), token);
                if (content == null) throw new IllegalStateException("Missing Git blob for " + file.filename());
                if (content.isBlank() || !fileCloneService.isIngestable(file.filename(), content.getBytes(StandardCharsets.UTF_8).length)) continue;
                String extension = fileCloneService.extensionOf(file.filename());
                for (var chunk : chunkingService.chunkFile(file.filename(), content)) pending.add(new PendingChunk(extension,chunk));
            }
            List<CodeChunk> staged = new ArrayList<>(pending.size());
            if (embeddingBatchSize < 1) throw new IllegalStateException("Embedding batch size must be positive");
            for (int start=0; start<pending.size(); start+=embeddingBatchSize) {
                var batch=pending.subList(start, Math.min(start+embeddingBatchSize,pending.size()));
                List<float[]> vectors;
                try {
                    vectors=embedBatchWithRetry(batch);
                    if (vectors.size()!=batch.size()) throw new IllegalStateException("Embedding count mismatch");
                } catch (EmbeddingRetryPolicy.RetryExhaustedException | org.springframework.ai.retry.NonTransientAiException e) {
                    throw e;   // a limited or misconfigured provider is not helped by one call per chunk
                } catch (RuntimeException e) {
                    log.warn("Batch embedding failed, retrying the batch one item at a time", e);
                    vectors=new ArrayList<>(batch.size());
                    for (var item:batch) vectors.add(embedSingleWithRetry(item.chunk().content()));
                }
                for (int i=0; i<batch.size(); i++) {
                    var item=batch.get(i);
                    var chunk=item.chunk();
                    staged.add(CodeChunk.builder().repository(repo).filePath(chunk.filePath())
                            .language(IngestionService.toLanguage(item.extension())).content(chunk.content())
                            .chunkIndex(chunk.chunkIndex()).startLine(chunk.startLine()).endLine(chunk.endLine())
                            .embedding(vectors.get(i)).build());
                }
            }
            snapshotWriter.publishChanges(repoId,repo.getLastCommitSha(),latestSha,affected,staged);
            persistInFinally = false;
            cacheService.evictUserReposCache();
            log.info("Published {} changed paths at revision {}", affected.size(), latestSha);

        } catch (Exception e) {
            log.error("Sync failed for repo {}: {}", repoId, e.getMessage(), e);
            // If fallback itself failed before it persisted its new state,
            // save the error below rather than leaving the previous READY row
            // marked as syncing.
            if (repo != null && persistInFinally) {
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

    private List<float[]> embedBatchWithRetry(List<PendingChunk> chunks) {
        List<String> texts = chunks.stream().map(item -> item.chunk().content()).toList();
        return retryPolicy.execute(() -> embeddingService.embedBatch(texts));
    }

    private float[] embedSingleWithRetry(String text) {
        return retryPolicy.execute(() -> embeddingService.embed(text));
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

    private record PendingChunk(String extension, ChunkingService.Chunk chunk) {}
}
