package com.repomind.repomind.service;

import java.util.List;
import java.util.UUID;

// Interface following Dependency Inversion Principle
// ChatService depends on this abstraction, not on Redis directly
// If you need to swap Redis for another storage, only the implementation changes
public interface ConversationMemoryService {

    //Represents one message in conversation History
    //Using a record for immutability and simplicity,value-based equality, and built-in toString, equals, and hashCode methods
    record MemoryMessage(String role, String content) {}

    //append a mesage to conversation history
    void addMessage(UUID conversationId, String role, String content);

    //Rerieve the recent conversation history for a given conversationId
    // returns at most maxMessages messages, ordered from oldest to newest
    // This is the sliding window — older messages beyond the limit are dropped
    List<MemoryMessage> getRecentMessages(UUID conversationId, int maxMessages);

    //clear all history for a conversationId, used when starting a new conversation or resetting context
    void clearConversation(UUID conversationId);
}
