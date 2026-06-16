package com.repomind.repomind.controller;


import com.repomind.repomind.dto.request.IngestRequest;
import com.repomind.repomind.dto.response.RepoStatusResponse;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.service.ingestion.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
// @CrossOrigin allows your React frontend (port 5173 in dev) to call this backend (port 8080)
// Browsers block cross-origin requests by default — this annotation lifts that block
// In production both are on the same domain so this does nothing, but it does not hurt
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})

public class IngestionController {

    private final IngestionService ingestionService;
    private final RepoJpaRepository repoRepository;

    @PostMapping("/ingest")
    public ResponseEntity<RepoStatusResponse> ingest(@Valid @RequestBody IngestRequest request){
        // @Valid triggers the @NotBlank and @Pattern checks on IngestRequest
        // If validation fails, Spring returns 400 automatically before this runs

        String repoName = extractRepoName(request.getGithubUrl());

        // Save the repo row immediately — gives it a UUID right now
        // Status starts as PENDING from @PrePersist in the entity
        RepoEntity repo = RepoEntity.builder()
                .githubUrl(request.getGithubUrl())
                .repoName(repoName)
                .build();

        repo = repoRepository.save(repo);

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

    @GetMapping
    public ResponseEntity<List<RepoStatusResponse>> listAll(){
        List<RepoStatusResponse> list = repoRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(list);
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
}
