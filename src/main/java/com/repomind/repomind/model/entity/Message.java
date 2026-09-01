package com.repomind.repomind.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    // "user" or "assistant" — matches what the LLM API expects
    @Column(nullable = false)
    private String role;

    @Column(nullable = false,columnDefinition = "TEXT")
    private String content;

    // Comma-separated file paths used as sources for this response
    // Example: "src/AuthService.java,src/SecurityConfig.java"
    private String sources;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }


}
