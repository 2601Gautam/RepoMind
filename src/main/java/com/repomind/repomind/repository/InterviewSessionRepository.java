package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    // All sessions for a specific user on a specific repo
    // Used to show history: "you generated these interview sets before"
    List<InterviewSession> findByUserIdAndRepositoryIdOrderByCreatedAtDesc(
            UUID userId, UUID repoId
    );

    // All sessions for a user across all repos
    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
