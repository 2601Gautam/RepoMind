package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.ChatRequest;
import com.repomind.repomind.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request){

        // Basic validation — return 400 if required fields are missing
        if (request.getRepoId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ChatService.ChatResult result = chatService.chat(
                    request.getRepoId(),
                    request.getMessage(),
                    request.getConversationId()  // null on first message, UUID after that
            );

            return ResponseEntity.ok(new ChatResponse(
                    result.answer(),
                    result.sources(),
                    result.conversationId()
            ));

        } catch (RuntimeException e) {
            // Return 400 with the error message so frontend can display it
            // Example: "Repository is not ready yet. Current status: PROCESSING"
//            log.error("Chat error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ChatResponse(e.getMessage(), List.of(), null)
            );
        }
    }

    public record ChatResponse(
            String answer,
            List<String> sources,
            UUID conversationId
    ) {}

}
