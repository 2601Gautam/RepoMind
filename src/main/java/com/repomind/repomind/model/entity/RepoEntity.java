package com.repomind.repomind.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private IngestionStatus status;

    @Column(name = "github_url", nullable = false)
    private String githubUrl;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "processed_files")
    private  Integer processedFiles;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // The commit SHA that was last fully embedded — sync diffs against this.
    // Captured for free from the clone that full ingestion already does.
    @Column(name = "last_commit_sha", length = 40)
    private String lastCommitSha;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    // Deliberately NOT part of `status`. Chat's guard checks status == READY;
    // keeping sync-in-progress out of that enum means chat keeps working for
    // the entire duration of a sync. Purely for UI ("Syncing…" badge) and to
    // stop a second sync firing on top of a running one.
    @Column(name = "syncing", nullable = false)
    private boolean syncing;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = IngestionStatus.PENDING;
        if (totalFiles == null) totalFiles = 0;
        if (processedFiles == null) processedFiles = 0;
        if (totalChunks == null) totalChunks = 0;
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    public enum IngestionStatus {
        PENDING,
        PROCESSING,
        READY,
        FAILED
    }
}