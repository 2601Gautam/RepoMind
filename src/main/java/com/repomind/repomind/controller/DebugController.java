package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.DebugRequest;
import com.repomind.repomind.dto.response.DebugResponse;
import com.repomind.repomind.service.DebugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {
    private final DebugService debugService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@Valid @RequestBody DebugRequest request){
        try{
            DebugResponse response = debugService.analyzeError(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Debug analysis failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
