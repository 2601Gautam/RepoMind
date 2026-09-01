package com.repomind.repomind.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {
    private UUID repoId;
    private String message;
    private UUID conversationId;
}
