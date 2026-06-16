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
@Data // Lombok: generates all getters, setters, toString, equals, hashCode
@Builder  // Lombok: provides a builder pattern for object creation
@NoArgsConstructor // Lombok: generates a no-args constructor , jpa requires a no-args constructor for entity classes
@AllArgsConstructor // Lombok: generates an all-args constructor, needed by @Builder to work
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

    // This method runs automatically just before JPA saves a NEW row (INSERT)
    // Sets both timestamps and default status so you never forget to set them
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = IngestionStatus.PENDING;
        if (totalFiles == null) totalFiles = 0;
        if (processedFiles == null) processedFiles = 0;
        if (totalChunks == null) totalChunks = 0;
    }
    // This method runs automatically just before JPA saves changes to an EXISTING row (UPDATE)
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    // Enum inside the entity because it only makes sense in this context
    // These are the exact strings stored in the status column
    public enum IngestionStatus {
        PENDING,     // row created, background job not started yet
        PROCESSING,  // currently cloning and embedding
        READY,       // all chunks stored, chat available
        FAILED       // something went wrong, check error_message column
    }
}
