package com.repomind.repomind.controller;


import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.dto.request.IngestRequest;
import com.repomind.repomind.dto.response.RepoStatusResponse;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.model.entity.UserRepo;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.ingestion.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
// @CrossOrigin allows your React frontend (port 5173 in dev) to call this backend (port 8080)
// Browsers block cross-origin requests by default — this annotation lifts that block
// In production both are on the same domain so this does nothing, but it does not hurt
@Slf4j
public class IngestionController {

    private final UserRepoRepository userRepoRepository;
    private final IngestionService ingestionService;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;

    @RateLimit(requests = 2, windowSeconds = 3600)  // 2 per hour
    @PostMapping("/ingest")
    public ResponseEntity<RepoStatusResponse> ingest(@Valid @RequestBody IngestRequest request,
                                                     @AuthenticationPrincipal User currentUser){
        // @Valid triggers the @NotBlank and @Pattern checks on IngestRequest
        // If validation fails, Spring returns 400 automatically before this runs

        String repoName = extractRepoName(request.getGithubUrl());

        Optional<RepoEntity> existing = repoRepository
                .findFirstByGithubUrlOrderByCreatedAtDesc(request.getGithubUrl());

        if(existing.isPresent())
        {
            RepoEntity existingRepo = existing.get();

            if(existingRepo.getStatus() == RepoEntity.IngestionStatus.READY)
            {
                // Give this user access to the existing repo without re-ingesting
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
                ingestionService.ingestAsync(existingRepo.getId(),
                        request.getGithubUrl(), request.getToken());
                return ResponseEntity.accepted().body(toDto(existingRepo));

            }
        }
        // Save the repo row immediately — gives it a UUID right now
        // Status starts as PENDING from @PrePersist in the entity
        RepoEntity repo = RepoEntity.builder()
                .githubUrl(request.getGithubUrl())
                .repoName(repoName)
                .build();

        repo = repoRepository.save(repo);
        grantUserAccess(currentUser, repo);

        // This fires the background job and returns IMMEDIATELY
        // The HTTP response goes back to the client before ingestion even starts
        // The client then polls /status every few seconds to track progress
        ingestionService.ingestAsync(repo.getId(), request.getGithubUrl(), request.getToken());

        // 202 Accepted = "I received your request and started working, but not done yet"
        // More honest than 200 OK which implies the work is complete
        return  ResponseEntity.accepted().body(toDto(repo));
    }

    @GetMapping("/{repoId}/status")
    public ResponseEntity<RepoStatusResponse> getStatus(@PathVariable UUID repoId)
    {
        return repoRepository.findById(repoId)
                .map(repo -> ResponseEntity.ok(toDto(repo)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Replace listAll to return only THIS user's repos:
    @GetMapping
    public ResponseEntity<List<RepoStatusResponse>> listAll(
            @AuthenticationPrincipal User currentUser){

        List<RepoStatusResponse> list = userRepoRepository
                .findReposByUserId(currentUser.getId())
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    // Helper — grants a user access to a repo, ignoring if they already have it

    private void grantUserAccess(User user,RepoEntity repo){
        if(!userRepoRepository.existsByUserIdAndRepoId(user.getId(),repo.getId())){
            userRepoRepository.save(UserRepo.builder()
                    .user(user)
                    .repo(repo)
                    .build());
        }
    }

    // Converts entity → DTO
    // Entity is your internal DB model, DTO is what the API exposes
    // Keeping them separate means DB changes don't break your API contract
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
