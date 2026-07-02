package com.repomind.repomind.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InterviewSessionDto {
    private UUID id;
    private UUID repoId;
    private String repoName;
    private String difficulty;
    private List<InterviewQuestionDto> questions;
    private LocalDateTime createdAt;
}