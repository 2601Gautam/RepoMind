package com.repomind.repomind.controller;

import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.dto.request.ChatRequest;
import com.repomind.repomind.dto.response.ChatHistoryResponse;
import com.repomind.repomind.model.entity.Conversation;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.service.ChatService;
import com.repomind.repomind.service.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatService chatService;
    private final ConversationMemoryService memoryService;

    @RateLimit(requests = 10, windowSeconds = 60)
    // produces = TEXT_EVENT_STREAM_VALUE tells Spring:
    // return type Flux<String> should be written as SSE, not buffered
    // The browser receives each Flux emission as a separate SSE event
    // This is what makes the "typing" effect work
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request,
    @AuthenticationPrincipal User currentUser){

        // Basic validation — return 400 if required fields are missing
        if (request.getRepoId() == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just("{\"type\":\"error\",\"content\":\"Invalid request\"}");
        }
        log.debug("Chat request from user {} for repo {}", currentUser.getId(), request.getRepoId());

        return chatService.streamChat(
                request.getRepoId(),
                request.getMessage(),
                request.getConversationId(),
                currentUser
        );
    }

    /**
     * GET /api/chat/history/{repoId}
     * Returns the most recent conversation + messages for the current user + repo.
     * Called by the frontend on page load to restore chat history from the database.
     * Returns 204 No Content if no conversation exists yet.
     */
    @GetMapping("/history/{repoId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal User currentUser) {
        return chatService.getChatHistory(repoId, currentUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // Clear conversation memory — "Start new conversation" button
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> clearConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal User currentUser) {
        chatService.clearConversation(conversationId,currentUser);
        return ResponseEntity.ok(Map.of("message", "Conversation cleared"));
    }

}
