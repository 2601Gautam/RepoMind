package com.repomind.repomind.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// This entity represents the user_repos join table
// It answers the question: "which repos does this user have access to?"
// A user gets a row here when they:
// 1. Submit a new repo URL (they become the owner)
// 2. Submit a URL that was already ingested (they get access to existing embeddings)
@Entity
@Table(name = "user_repos",
uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "repo_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private RepoEntity repo;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }

}
