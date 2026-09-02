package com.repomind.repomind.controller;


import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.dto.request.IngestRequest;
import com.repomind.repomind.dto.request.SyncRequest;
import com.repomind.repomind.dto.response.RepoStatusResponse;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final UserRepoRepository userRepoRepository;
    private final IngestionQueuePublisher ingestionQueuePublisher;
    private final SyncService syncService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;
    private final S3StorageService s3StorageService;
    private final GitHubApiService gitHubApiService;

    @RateLimit(requests = 2, windowSeconds = 3600)  // 2 per hour
    @PostMapping("/ingest")
    @CacheEvict(value = "userRepos", key = "#currentUser.id")
    public ResponseEntity<RepoStatusResponse> ingest(@Valid @RequestBody IngestRequest request,
                                                     @AuthenticationPrincipal User currentUser){
        String githubUrl = request.getGithubUrl();
        if (githubUrl.endsWith(".git")) {
            githubUrl = githubUrl.substring(0, githubUrl.length() - 4);
        }

        request.setGithubUrl(githubUrl);
        String repoName = extractRepoName(request.getGithubUrl());

        Optional<RepoEntity> existing = repoRepository
                .findFirstByGithubUrlOrderByCreatedAtDesc(request.getGithubUrl());

        if (existing.isPresent()) {
            return reuseExistingRepo(existing.get(), request, currentUser);
        }
        GitHubApiService.RepositoryMetadata metadata;
        try {
            // Establish the privacy classification before persisting the new
            // repository. The job is still queued through the existing SQS flow.
            metadata = gitHubApiService.getRepositoryMetadata(request.getGithubUrl(), request.getToken());
        } catch (GitHubApiService.RepositoryAccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }

        // Save the repo row immediately — gives it a UUID right now
        // Status starts as PENDING from @PrePersist in the entity
        RepoEntity repo = RepoEntity.builder()
                .githubUrl(request.getGithubUrl())
                .repoName(repoName)
                .isPrivate(metadata.isPrivate())
                .build();

        try {
            // Flush now so the database's unique index decides concurrent
            // creates before this request grants access or queues any work.
            repo = repoRepository.saveAndFlush(repo);
        } catch (DataIntegrityViolationException e) {
            // Another request won the same-url insert race. PostgreSQL only
            // reports this after that transaction commits, so re-read its row
            // and follow the ordinary existing-repository path. This request
            // must not enqueue a second ingestion job.
            RepoEntity concurrentlyCreatedRepo = repoRepository
                    .findFirstByGithubUrlOrderByCreatedAtDesc(request.getGithubUrl())
                    .orElseThrow(() -> e);
            return reuseExistingRepo(concurrentlyCreatedRepo, request, currentUser);
        }
        grantUserAccess(currentUser, repo);

        // Publish the durable job before returning 202. The SQS worker picks it
        // up asynchronously, possibly after this web process restarts.
        if (!enqueueIngestion(repo, request.getGithubUrl(), request.getToken())) {
            return ResponseEntity.status(503).body(toDto(repo));
        }

        // 202 Accepted = "I received your request and started working, but not done yet"
        // More honest than 200 OK which implies the work is complete
        return  ResponseEntity.accepted().body(toDto(repo));
    }

    private ResponseEntity<RepoStatusResponse> reuseExistingRepo(
            RepoEntity existingRepo,
            IngestRequest request,
            User currentUser
    ) {
        // A URL match is a deduplication key, not proof that this user can
        // read its contents. Legacy rows (null) are also fail-closed: their
        // old privacy state is unknown, so they require verification too.
        // This executes before every path that can grant UserRepo access.
        if (requiresGitHubAccessVerification(existingRepo)
                && !hasVerifiedGitHubAccess(existingRepo, request.getToken())) {
            return ResponseEntity.status(403).build();
        }

        if (existingRepo.getStatus() == RepoEntity.IngestionStatus.READY) {
            // Public repos deduplicate immediately. Private repos reach here
            // only after the token was verified against this exact URL.
            grantUserAccess(currentUser, existingRepo);
            cleanupDuplicates(request.getGithubUrl(), existingRepo.getId());
            return ResponseEntity.ok(toDto(existingRepo));
        }

        if (existingRepo.getStatus() == RepoEntity.IngestionStatus.PENDING
                || existingRepo.getStatus() == RepoEntity.IngestionStatus.PROCESSING) {
            grantUserAccess(currentUser, existingRepo);
            return ResponseEntity.accepted().body(toDto(existingRepo));
        }

        if (existingRepo.getStatus() == RepoEntity.IngestionStatus.FAILED) {
            cleanupDuplicates(request.getGithubUrl(), existingRepo.getId());
            chunkRepository.deleteByRepositoryId(existingRepo.getId());
            existingRepo.setStatus(RepoEntity.IngestionStatus.PENDING);
            existingRepo.setErrorMessage(null);
            existingRepo.setProcessedFiles(0);
            existingRepo.setTotalFiles(0);
            existingRepo.setTotalChunks(0);
            repoRepository.save(existingRepo);
            grantUserAccess(currentUser, existingRepo);
            // Other users sharing this repo may have FAILED cached in Redis
            // Evict all so they see the fresh PENDING status immediately
            cacheService.evictUserReposCache();
            if (!enqueueIngestion(existingRepo, request.getGithubUrl(), request.getToken())) {
                return ResponseEntity.status(503).body(toDto(existingRepo));
            }
            return ResponseEntity.accepted().body(toDto(existingRepo));
        }

        throw new IllegalStateException("Unsupported repository status: " + existingRepo.getStatus());
    }

    /**
     * A 202 response is only honest after SQS accepted the message. If it did
     * not, persist FAILED so the frontend never polls forever for a job that
     * cannot run.
     */
    private boolean enqueueIngestion(RepoEntity repo, String githubUrl, String token) {
        try {
            ingestionQueuePublisher.enqueue(repo.getId(), githubUrl, token);
            return true;
        } catch (Exception e) {
            log.error("Could not enqueue ingestion for repo {}", repo.getId(), e);
            repo.setStatus(RepoEntity.IngestionStatus.FAILED);
            repo.setErrorMessage("Could not queue ingestion job. Please try again.");
            repo.setIngestionLeaseUntil(null);
            repoRepository.save(repo);
            cacheService.evictUserReposCache();
            return false;
        }
    }

    // Incremental sync — diffs against last_commit_sha instead of re-cloning.
    // Stays a lightweight direct @Async call for the common (small-diff) case;
    // only its full-re-ingest fallback goes through SQS. See SyncService.
    @RateLimit(requests = 10, windowSeconds = 3600)
    @PostMapping("/{repoId}/sync")
    public ResponseEntity<RepoStatusResponse> sync(@PathVariable UUID repoId,
                                                   @RequestBody(required = false) SyncRequest request,
                                                   @AuthenticationPrincipal User currentUser) {
        RepoEntity repo = userRepoRepository.findByUserIdAndRepoId(currentUser.getId(), repoId)
                .flatMap(userRepo -> repoRepository.findById(repoId))
                .orElse(null);

        if (repo == null) {
            return ResponseEntity.notFound().build();
        }

        if (repo.getStatus() != RepoEntity.IngestionStatus.READY) {
            return ResponseEntity.status(409).body(toDto(repo));
        }

        if (repo.isSyncing()) {
            return ResponseEntity.accepted().body(toDto(repo));
        }

        String token = request != null ? request.getToken() : null;
        syncService.syncAsync(repoId, token);
        return ResponseEntity.accepted().body(toDto(repo));
    }

    @GetMapping("/{repoId}/status")
    public ResponseEntity<RepoStatusResponse> getStatus(@PathVariable UUID repoId,
                                                        @AuthenticationPrincipal User currentUser)
    {
        return userRepoRepository.findByUserIdAndRepoId(currentUser.getId(),repoId)
                .flatMap(userRepo -> repoRepository.findById(repoId))
                .map(repo -> ResponseEntity.ok(toDto(repo)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Replace listAll to return only THIS user's repos:
    @GetMapping
    @Cacheable(value = "userRepos",key = "#currentUser.id")
    public List<RepoStatusResponse> listAll(
            @AuthenticationPrincipal User currentUser){

        List<RepoStatusResponse> list = userRepoRepository
                .findReposByUserId(currentUser.getId())
                .stream()
                .map(this::toDto)
                .toList();
        return list;
    }

    // Returns a short-lived (15 min) presigned S3 URL to download the zipped
    // source that was archived when this repo was ingested. 404 if the user
    // doesn't have access to the repo, or if no archive exists yet (e.g. the
    // S3 upload failed at ingestion time, or this repo predates the feature).
    @GetMapping("/{repoId}/archive")
    public ResponseEntity<Map<String, Object>> getArchiveDownloadUrl(@PathVariable UUID repoId,
                                                                     @AuthenticationPrincipal User currentUser) {
        boolean hasAccess = userRepoRepository.findByUserIdAndRepoId(currentUser.getId(), repoId).isPresent();
        if (!hasAccess) {
            return ResponseEntity.notFound().build();
        }

        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repo not found: " + repoId));

        if (repo.getArchiveKey() == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "No archive available yet for this repository."));
        }

        String url = s3StorageService.generatePresignedDownloadUrl(repo.getArchiveKey());
        return ResponseEntity.ok(Map.of(
                "downloadUrl", url,
                "expiresInMinutes", 15
        ));
    }

    // Remove this user's access to a repo.
    // If no other users have access, delete the repo entity entirely.
    @DeleteMapping("/{repoId}")
    @CacheEvict(value = "userRepos", key = "#currentUser.id")
    public ResponseEntity<Void> removeRepo(@PathVariable UUID repoId,
                                           @AuthenticationPrincipal User currentUser) {
        userRepoRepository.findByUserIdAndRepoId(currentUser.getId(), repoId)
                .ifPresent(userRepoRepository::delete);

        // If nobody else has this repo, clean it up entirely
        if (userRepoRepository.countByRepoId(repoId) == 0) {
            // Delete the S3 archive (if any) before dropping the row that
            // holds its key — otherwise the object becomes unreachable and
            // sits in the bucket forever with no reference left to find it.
            repoRepository.findById(repoId).ifPresent(repo ->
                    s3StorageService.deleteArchive(repo.getArchiveKey()));

            chunkRepository.deleteByRepositoryId(repoId);
            repoRepository.deleteById(repoId);
        }

        return ResponseEntity.noContent().build();
    }

    // Helper — authorization must already have succeeded before this call.
    // The database's unique constraint makes the insert idempotent when two
    // otherwise-valid requests race to grant the same mapping.

    private void grantUserAccess(User user,RepoEntity repo){
        userRepoRepository.grantAccess(user.getId(), repo.getId());
    }

    private boolean requiresGitHubAccessVerification(RepoEntity repo) {
        return !Boolean.FALSE.equals(repo.getIsPrivate());
    }

    private boolean hasVerifiedGitHubAccess(RepoEntity repo, String token) {
        return token != null && !token.isBlank()
                && gitHubApiService.canAccessRepository(repo.getGithubUrl(), token);
    }

    // Converts entity → DTO
    private RepoStatusResponse toDto(RepoEntity repo) {
        return RepoStatusResponse.builder()
                .id(repo.getId())
                .githubUrl(repo.getGithubUrl())
                .repoName(repo.getRepoName())
                .status(repo.getStatus().toString())
                .totalFiles(repo.getTotalFiles())
                .processedFiles(repo.getProcessedFiles())
                .totalChunks(repo.getTotalChunks())
                .errorMessage(repo.getErrorMessage())
                .createdAt(repo.getCreatedAt())
                .lastSyncedAt(repo.getLastSyncedAt())
                .syncing(repo.isSyncing())
                .hasArchive(repo.getArchiveKey() != null)
                .build();
    }
    private String extractRepoName(String url) {
        // "https://github.com/facebook/react" → "facebook/react"
        String[] parts = url.replaceAll("/$", "").split("/");
        return parts.length >= 2
                ? parts[parts.length - 2] + "/" + parts[parts.length - 1]
                : url;
    }
    private void cleanupDuplicates(String githubUrl, UUID keepId) {
        List<RepoEntity> duplicates = repoRepository
                .findByGithubUrlAndIdNot(githubUrl, keepId);

        if (!duplicates.isEmpty()) {
            log.info("Removing {} duplicate entries for {}",
                    duplicates.size(), githubUrl);
            repoRepository.deleteAll(duplicates);
        }
    }
}
