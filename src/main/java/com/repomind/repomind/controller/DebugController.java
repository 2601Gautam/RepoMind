package com.repomind.repomind.controller;

import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.dto.request.DebugRequest;
import com.repomind.repomind.dto.response.DebugResponse;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.DebugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {
    private final DebugService debugService;
    private final UserRepoRepository userRepoRepository;
    @RateLimit(requests = 5, windowSeconds = 60)
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyze(@Valid @RequestBody DebugRequest request,
                                @AuthenticationPrincipal User currentUser) {
        // Inside the method (assuming userRepoRepository is injected):
        if (request.getRepoId() != null) {
            userRepoRepository.findByUserIdAndRepoId(currentUser.getId(), request.getRepoId())
                    .orElseThrow(() -> new RuntimeException("Access denied"));
        }
        return debugService.streamAnalysis(request);
    }
}
