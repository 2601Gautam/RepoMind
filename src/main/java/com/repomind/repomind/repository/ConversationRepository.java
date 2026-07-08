package com.repomind.repomind.repository;


import com.repomind.repomind.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    long countByRepositoryUserId(UUID userId);
}
