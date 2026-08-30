package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final GitHubApiService gitHubApiService;
    private final FileCloneService fileCloneService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;
    private final IngestionService ingestionService; // fallback path only

    @Async
    public void syncAsync(UUID repoId, String token) {
        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repo not found: " + repoId));

        if (repo.getLastCommitSha() == null) {
            log.info("Repo {} has no baseline commit — falling back to full ingest", repoId);
            fallbackToFullReingest(repo, token);
            return;
        }

        repo.setSyncing(true);
        repoRepository.save(repo); // status stays READY — chat keeps working throughout

        try {
            GitHubApiService.RepoCoordinates coords = gitHubApiService.parse(repo.getGithubUrl());
            String latestSha = gitHubApiService.getLatestCommitSha(coords, token);

            if (latestSha.equals(repo.getLastCommitSha())) {
                log.info("Repo {} already at latest commit {}", repoId, latestSha);
                repo.setLastSyncedAt(LocalDateTime.now());
                return; // zero clone, zero embeddings — nothing changed
            }

            GitHubApiService.CompareResult diff =
                    gitHubApiService.compare(coords, repo.getLastCommitSha(), latestSha, token);

            if (diff.tooLargeToDiff()) {
                log.warn("Diff for repo {} too large/untrustworthy — falling back to full re-ingest", repoId);
                fallbackToFullReingest(repo, token);
                return;
            }

            int filesTouched = 0;
            for (GitHubApiService.ChangedFile cf : diff.files()) {
                switch (cf.status()) {
                    case "removed" ->
                            chunkRepository.deleteByRepositoryIdAndFilePath(repoId, cf.filename());
                    case "renamed" -> {
                        chunkRepository.deleteByRepositoryIdAndFilePath(repoId, cf.previousFilename());
                        replaceFileChunks(repo, coords, cf.filename(), cf.blobSha(), token);
                    }
                    default ->
                            replaceFileChunks(repo, coords, cf.filename(), cf.blobSha(), token);
                }
                filesTouched++;
            }

            repo.setLastCommitSha(latestSha);
            repo.setLastSyncedAt(LocalDateTime.now());
            repo.setTotalChunks((int) chunkRepository.countByRepositoryId(repoId));
            cacheService.evictUserReposCache();

            log.info("Sync complete for repo {}: {} files touched ({} -> {})",
                    repoId, filesTouched, repo.getLastCommitSha(), latestSha);

        } catch (Exception e) {
            log.error("Sync failed for repo {}: {}", repoId, e.getMessage(), e);
            repo.setErrorMessage("Sync failed: " + e.getMessage());
        } finally {
            repo.setSyncing(false);
            repoRepository.save(repo);
        }
    }

    private void replaceFileChunks(RepoEntity repo, GitHubApiService.RepoCoordinates coords,
                                   String filePath, String blobSha, String token) {
        List<UUID> staleChunkIds = chunkRepository.findIdsByRepositoryIdAndFilePath(repo.getId(), filePath);
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

            for (ChunkingService.Chunk chunk : chunks) {
                try {
                    float[] embedding = embeddingService.embed(chunk.content());
                    chunkRepository.save(CodeChunk.builder()
                            .repository(repo)
                            .filePath(chunk.filePath())
                            .language(IngestionService.toLanguage(ext))
                            .content(chunk.content())
                            .chunkIndex(chunk.chunkIndex())
                            .startLine(chunk.startLine())
                            .endLine(chunk.endLine())
                            .embedding(embedding)
                            .build());
                } catch (Exception e) {
                    log.warn("Skipping chunk {} in {}: {}", chunk.chunkIndex(), filePath, e.getMessage());
                }
            }
            deleteStale(staleChunkIds);

        } catch (Exception e) {
            log.warn("Could not re-embed {} — keeping previous version: {}", filePath, e.getMessage());
        }
    }

    private void deleteStale(List<UUID> ids) {
        if (!ids.isEmpty()) chunkRepository.deleteByIdIn(ids);
    }

    private void fallbackToFullReingest(RepoEntity repo, String token) {
        chunkRepository.deleteByRepositoryId(repo.getId());
        repo.setStatus(RepoEntity.IngestionStatus.PENDING);
        repo.setProcessedFiles(0);
        repo.setTotalChunks(0);
        repoRepository.save(repo);
        ingestionService.ingestAsync(repo.getId(), repo.getGithubUrl(), token);
    }
}