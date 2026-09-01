package com.repomind.repomind.repository;


import com.repomind.repomind.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT COUNT(DISTINCT c)
            FROM Conversation c
            JOIN UserRepo ur ON ur.repo = c.repository
            WHERE ur.user.id = :userId
            """)
    long countByUserId(@Param("userId") UUID userId);

    // Returns all conversations for a user+repo, newest first (used to pick the latest)
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.repository.id = :repoId
            AND c.user.id = :userId
            ORDER BY c.createdAt DESC
            """)
    List<Conversation> findByRepositoryIdAndUserIdOrderByCreatedAtDesc(
            @Param("repoId") UUID repoId,
            @Param("userId") UUID userId);

}
