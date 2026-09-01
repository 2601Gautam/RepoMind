package com.repomind.repomind.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ChatHistoryResponse {

    private UUID conversationId;

    private List<MessageDto> messages;

    @Data
    @Builder
    public static class MessageDto {
        private String role;      // "user" or "assistant"
        private String content;
        private String sources;   // comma-separated file paths, may be null
    }
}
