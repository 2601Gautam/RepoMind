package com.repomind.repomind.controller;

import com.repomind.repomind.service.ChatService;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request){

        // Basic validation — return 400 if required fields are missing
        if (request.repoId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ChatService.ChatResult result = chatService.chat(
                    request.repoId(),
                    request.message(),
                    request.conversationId()  // null on first message, UUID after that
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
    public record ChatRequest(
            UUID repoId,
            String message,
            UUID conversationId
    ) {}

    public record ChatResponse(
            String answer,
            List<String> sources,
            UUID conversationId
    ) {}


}
