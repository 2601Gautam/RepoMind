package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    // Returns all messages in a conversation ordered oldest first
    // This is what you send back to the frontend as chat history
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
