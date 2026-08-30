package com.repomind.repomind.controller;


import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.dto.request.IngestRequest;
import com.repomind.repomind.dto.request.SyncRequest;
import com.repomind.repomind.dto.response.RepoStatusResponse;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.model.entity.UserRepo;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.ingestion.IngestionService;
import com.repomind.repomind.service.ingestion.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final UserRepoRepository userRepoRepository;
    private final IngestionService ingestionService;
    private final SyncService syncService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final CacheService cacheService;

    @RateLimit(requests = 2, windowSeconds = 3600)
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

        if(existing.isPresent())
        {
            RepoEntity existingRepo = existing.get();

            if(existingRepo.getStatus() == RepoEntity.IngestionStatus.READY)
            {
                grantUserAccess(currentUser,existingRepo);
                cleanupDuplicates(request.getGithubUrl(),existingRepo.getId());
                return ResponseEntity.ok(toDto(existingRepo));
            }

            if (existingRepo.getStatus() == RepoEntity.IngestionStatus.PROCESSING) {
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
                cacheService.evictUserReposCache();
                ingestionService.ingestAsync(existingRepo.getId(),
                        request.getGithubUrl(), request.getToken());
                return ResponseEntity.accepted().body(toDto(existingRepo));

            }
        }
        RepoEntity repo = RepoEntity.builder()
                .githubUrl(request.getGithubUrl())
                .repoName(repoName)
                .build();

        repo = repoRepository.save(repo);
        grantUserAccess(currentUser, repo);

        ingestionService.ingestAsync(repo.getId(), request.getGithubUrl(), request.getToken());

        return  ResponseEntity.accepted().body(toDto(repo));
    }

    // Incremental sync — diffs against last_commit_sha instead of re-cloning.
    // See SyncService for the actual logic.
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

    @DeleteMapping("/{repoId}")
    @CacheEvict(value = "userRepos", key = "#currentUser.id")
    public ResponseEntity<Void> removeRepo(@PathVariable UUID repoId,
                                           @AuthenticationPrincipal User currentUser) {
        userRepoRepository.findByUserIdAndRepoId(currentUser.getId(), repoId)
                .ifPresent(userRepoRepository::delete);

        if (userRepoRepository.countByRepoId(repoId) == 0) {
            chunkRepository.deleteByRepositoryId(repoId);
            repoRepository.deleteById(repoId);
        }

        return ResponseEntity.noContent().build();
    }

    private void grantUserAccess(User user,RepoEntity repo){
        if(!userRepoRepository.existsByUserIdAndRepoId(user.getId(),repo.getId())){
            userRepoRepository.save(UserRepo.builder()
                    .user(user)
                    .repo(repo)
                    .build());
        }
    }

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
                .build();
    }
    private String extractRepoName(String url) {
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