package com.repomind.repomind.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private RepoEntity repository;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    private String language;

    // columndefinition = "TEXT" is used to store large text data in the database, as code chunks can be quite large and exceed the typical VARCHAR limit.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    // float[] in Java → vector(768) in PostgreSQL
    // The pgvector Java client dependency you added handles this type conversion
    // These 768 numbers are what pgvector searches through for similarity
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
