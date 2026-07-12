package com.repomind.repomind.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    // Lightweight, no DB/Redis dependency — just proves the JVM is up and
    // accepting requests. Used as a cron-ping target to keep the Render
    // free-tier instance from spinning down after ~15 min of inactivity.
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
