package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.ChatRequest;
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

    // Clear conversation memory — "Start new conversation" button
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> clearConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal User currentUser) {
        memoryService.clearConversation(conversationId);
        return ResponseEntity.ok(Map.of("message", "Conversation cleared"));
    }

}
