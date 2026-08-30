package com.repomind.repomind.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

import java.io.Serializable;

@Data
@Builder
public class RepoStatusResponse implements Serializable {
    private UUID id;
    private String githubUrl;
    private String repoName;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer totalChunks;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime lastSyncedAt;
    private boolean syncing;
}