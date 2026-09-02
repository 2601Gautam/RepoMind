package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.IngestRequest;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.S3StorageService;
import com.repomind.repomind.service.ingestion.GitHubApiService;
import com.repomind.repomind.service.ingestion.SyncService;
import com.repomind.repomind.service.queue.IngestionQueuePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionControllerTest {

    private static final String REPO_URL = "https://github.com/acme/private-repo";

    @Mock private UserRepoRepository userRepoRepository;
    @Mock private IngestionQueuePublisher ingestionQueuePublisher;
    @Mock private SyncService syncService;
    @Mock private RepoJpaRepository repoRepository;
    @Mock private CodeChunkRepository chunkRepository;
    @Mock private CacheService cacheService;
    @Mock private S3StorageService s3StorageService;
    @Mock private GitHubApiService gitHubApiService;

    private IngestionController controller;
    private User currentUser;

    @BeforeEach
    void setUp() {
        controller = new IngestionController(
                userRepoRepository,
                ingestionQueuePublisher,
                syncService,
                repoRepository,
                chunkRepository,
                cacheService,
                s3StorageService,
                gitHubApiService
        );
        currentUser = User.builder().id(UUID.randomUUID()).email("current@example.com").build();
    }

    @Test
    void readyPublicRepoGrantsAccessWithoutGithubToken() {
        RepoEntity repo = readyRepo(false);
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL))
                .thenReturn(Optional.of(repo));
        when(repoRepository.findByGithubUrlAndIdNot(REPO_URL, repo.getId())).thenReturn(List.of());

        ResponseEntity<?> response = controller.ingest(request(REPO_URL, null), currentUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(gitHubApiService);
        verify(userRepoRepository).grantAccess(currentUser.getId(), repo.getId());
    }

    @Test
    void readyPrivateRepoWithValidTokenGrantsAccessAfterExactRepositoryCheck() {
        RepoEntity repo = readyRepo(true);
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL))
                .thenReturn(Optional.of(repo));
        when(repoRepository.findByGithubUrlAndIdNot(REPO_URL, repo.getId())).thenReturn(List.of());
        when(gitHubApiService.canAccessRepository(REPO_URL, "valid-token")).thenReturn(true);

        ResponseEntity<?> response = controller.ingest(request(REPO_URL, "valid-token"), currentUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(gitHubApiService).canAccessRepository(REPO_URL, "valid-token");
        verify(userRepoRepository).grantAccess(currentUser.getId(), repo.getId());
    }

    @Test
    void readyPrivateRepoWithNoTokenIsForbiddenAndNeverGranted() {
        RepoEntity repo = readyRepo(true);
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL))
                .thenReturn(Optional.of(repo));

        ResponseEntity<?> response = controller.ingest(request(REPO_URL, null), currentUser);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(gitHubApiService, never()).canAccessRepository(any(), any());
        verify(userRepoRepository, never()).grantAccess(any(), any());
    }

    @Test
    void readyPrivateRepoWithInvalidTokenIsForbiddenAndNeverGranted() {
        RepoEntity repo = readyRepo(true);
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL))
                .thenReturn(Optional.of(repo));
        when(gitHubApiService.canAccessRepository(REPO_URL, "invalid-token")).thenReturn(false);

        ResponseEntity<?> response = controller.ingest(request(REPO_URL, "invalid-token"), currentUser);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(gitHubApiService).canAccessRepository(REPO_URL, "invalid-token");
        verify(userRepoRepository, never()).grantAccess(any(), any());
    }

    @Test
    void revokedOriginalIngesterDoesNotLetDifferentUserGainPrivateRepoAccess() {
        RepoEntity repo = readyRepo(true);
        User differentUser = User.builder().id(UUID.randomUUID()).email("different@example.com").build();
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL))
                .thenReturn(Optional.of(repo));
        when(gitHubApiService.canAccessRepository(REPO_URL, "revoked-or-invalid-token")).thenReturn(false);

        ResponseEntity<?> response = controller.ingest(
                request(REPO_URL, "revoked-or-invalid-token"), differentUser);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(gitHubApiService).canAccessRepository(REPO_URL, "revoked-or-invalid-token");
        verify(userRepoRepository, never()).grantAccess(any(), any());
    }

    @Test
    void newIngestionPersistsPrivacyFromGithubMetadata() {
        assertNewIngestionPrivacy(true, "valid-token");
    }

    @Test
    void newPublicIngestionPersistsPublicPrivacyFromGithubMetadataWithoutToken() {
        assertNewIngestionPrivacy(false, null);
    }

    @Test
    void concurrentSameUrlInsertReusesWinnerWithoutQueueingAnotherJob() {
        RepoEntity winner = RepoEntity.builder()
                .id(UUID.randomUUID())
                .githubUrl(REPO_URL)
                .repoName("acme/private-repo")
                .status(RepoEntity.IngestionStatus.PENDING)
                .isPrivate(false)
                .build();
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(gitHubApiService.getRepositoryMetadata(REPO_URL, null))
                .thenReturn(new GitHubApiService.RepositoryMetadata(false));
        when(repoRepository.saveAndFlush(any(RepoEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate github_url"));

        ResponseEntity<?> response = controller.ingest(request(REPO_URL, null), currentUser);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(repoRepository).saveAndFlush(any(RepoEntity.class));
        verify(userRepoRepository).grantAccess(currentUser.getId(), winner.getId());
        verify(ingestionQueuePublisher, never()).enqueue(any(), any(), any());
    }

    private void assertNewIngestionPrivacy(boolean isPrivate, String token) {
        UUID repoId = UUID.randomUUID();
        when(repoRepository.findFirstByGithubUrlOrderByCreatedAtDesc(REPO_URL)).thenReturn(Optional.empty());
        when(gitHubApiService.getRepositoryMetadata(REPO_URL, token))
                .thenReturn(new GitHubApiService.RepositoryMetadata(isPrivate));
        when(repoRepository.saveAndFlush(any(RepoEntity.class))).thenAnswer(invocation -> {
            RepoEntity repo = invocation.getArgument(0);
            repo.setId(repoId);
            repo.setStatus(RepoEntity.IngestionStatus.PENDING);
            return repo;
        });

        ResponseEntity<?> response = controller.ingest(request(REPO_URL, token), currentUser);

        ArgumentCaptor<RepoEntity> savedRepo = ArgumentCaptor.forClass(RepoEntity.class);
        verify(repoRepository).saveAndFlush(savedRepo.capture());
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(savedRepo.getValue().getIsPrivate()).isEqualTo(isPrivate);
        verify(ingestionQueuePublisher).enqueue(repoId, REPO_URL, token);
        verify(userRepoRepository).grantAccess(currentUser.getId(), repoId);
    }

    private RepoEntity readyRepo(Boolean isPrivate) {
        return RepoEntity.builder()
                .id(UUID.randomUUID())
                .githubUrl(REPO_URL)
                .repoName("acme/private-repo")
                .status(RepoEntity.IngestionStatus.READY)
                .isPrivate(isPrivate)
                .totalFiles(1)
                .processedFiles(1)
                .totalChunks(1)
                .build();
    }

    private IngestRequest request(String githubUrl, String token) {
        IngestRequest request = new IngestRequest();
        request.setGithubUrl(githubUrl);
        request.setToken(token);
        return request;
    }
}
