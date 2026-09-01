package com.repomind.repomind.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    // Nullable now — Google OAuth users have no password
    // LOCAL users always have a password hash
    @Column(name = "password_hash")
    private String passwordHash;

    private String name;

    // LOCAL = registered with email/password
    // GOOGLE = registered via Google OAuth2
    // GITHUB = registered via GitHub OAuth2
    @Column(nullable = false)
    @Builder.Default
    private String provider = "LOCAL";


    // Google's unique user ID — used to match returning Google users
    // null for LOCAL users
    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (provider == null) provider = "LOCAL";
    }
}
