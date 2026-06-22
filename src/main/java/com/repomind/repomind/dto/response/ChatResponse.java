package com.repomind.repomind.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ChatResponse {
    private String answer;
    private List<String> sources;
    private UUID conversationId;
}
