package com.repomind.repomind.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

// DTO exists separately from the Entity on purpose
// If you return the Entity directly, you expose all internal fields
// If your DB schema changes, your API response changes too — breaking the frontend
// DTO decouples API response shape from DB structure
@Data
@Builder
public class RepoStatusResponse {
    private UUID id;
    private String githubUrl;
    private String repoName;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer totalChunks;
    private String errorMessage;
    private LocalDateTime createdAt;
}
