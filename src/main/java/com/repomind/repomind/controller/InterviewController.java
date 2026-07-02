package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.InterviewRequest;
import com.repomind.repomind.dto.response.InterviewSessionDto;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Slf4j
public class InterviewController {
    private final InterviewService interviewService;
    // Generate new interview questions for a repo
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @Valid @RequestBody InterviewRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            InterviewSessionDto session = interviewService.generateQuestions(request, currentUser);
            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            log.error("Interview generation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get a previously generated session by ID
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(interviewService.getSession(sessionId, currentUser));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get all sessions for the current user
    @GetMapping("/sessions")
    public ResponseEntity<List<InterviewSessionDto>> getUserSessions(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(interviewService.getUserSessions(currentUser));
    }

}
