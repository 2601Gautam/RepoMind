package com.repomind.repomind.controller;

import com.repomind.repomind.dto.response.RepoStatusResponse;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/activity") @RequiredArgsConstructor
public class ActivityController {
    private final ActivityService activity;
    private final UserRepoRepository userRepos;
    public record Snapshot(List<RepoStatusResponse> repositories, List<ActivityService.Activity> tasks) {}

    @GetMapping
    public Snapshot snapshot(@AuthenticationPrincipal User user) {
        // Bypass the dashboard cache: reconnect must read authoritative status.
        var repos = userRepos.findReposByUserId(user.getId()).stream().map(r -> RepoStatusResponse.builder()
                .id(r.getId()).githubUrl(r.getGithubUrl()).repoName(r.getRepoName()).status(r.getStatus().name())
                .totalFiles(r.getTotalFiles()).processedFiles(r.getProcessedFiles()).totalChunks(r.getTotalChunks())
                .errorMessage(r.getErrorMessage()).createdAt(r.getCreatedAt()).lastSyncedAt(r.getLastSyncedAt())
                .syncMessage(r.getSyncMessage()).syncing(r.isSyncing()).hasArchive(r.getArchiveKey()!=null).build()).toList();
        return new Snapshot(repos,activity.recent(user.getId()));
    }
}

