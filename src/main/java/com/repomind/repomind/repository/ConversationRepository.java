package com.repomind.repomind.repository;


import com.repomind.repomind.model.entity.Conversation;
import com.repomind.repomind.model.entity.UserRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    @Query("""
            SELECT COUNT(DISTINCT c)
            FROM Conversation c
            JOIN UserRepo ur ON ur.repo = c.repository
            WHERE ur.user.id = :userId
            """)
    long countByUserId(@Param("userId") UUID userId);
}
