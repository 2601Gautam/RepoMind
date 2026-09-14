package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.queue.IngestionQueuePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock private GitHubApiService gitHubApiService;
    @Mock private FileCloneService fileCloneService;
    @Mock private ChunkingService chunkingService;
    @Mock private EmbeddingService embeddingService;
    @Mock private RepoJpaRepository repoRepository;
    @Mock private CodeChunkRepository chunkRepository;
    @Mock private CacheService cacheService;
    @Mock private IngestionQueuePublisher ingestionQueuePublisher;

    @Test
    void noBaselineFallbackReleasesSyncWithoutResavingStaleEntity() {
        UUID repoId = UUID.randomUUID();
        RepoEntity repo = RepoEntity.builder()
                .id(repoId)
                .githubUrl("https://github.com/acme/repo")
                .status(RepoEntity.IngestionStatus.READY)
                .syncing(true)
                .totalChunks(10)
                .build();
        when(repoRepository.findById(repoId)).thenReturn(Optional.of(repo));

        new SyncService(
                gitHubApiService,
                fileCloneService,
                chunkingService,
                embeddingService,
                repoRepository,
                chunkRepository,
                cacheService,
                ingestionQueuePublisher
        ).syncAsync(repoId, "token");

        assertThat(repo.getStatus()).isEqualTo(RepoEntity.IngestionStatus.PENDING);
        assertThat(repo.getTotalChunks()).isZero();
        verify(chunkRepository).deleteByRepositoryId(repoId);
        verify(ingestionQueuePublisher).enqueue(repoId, repo.getGithubUrl(), "token");
        // fallbackToFullReingest saves PENDING before publishing. The sync
        // finally block must not save it again after the SQS worker can run.
        verify(repoRepository, times(1)).save(repo);
        verify(repoRepository).releaseIncrementalSync(repoId);
    }
}
