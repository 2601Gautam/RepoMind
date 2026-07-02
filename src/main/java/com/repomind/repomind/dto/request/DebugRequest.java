package com.repomind.repomind.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class DebugRequest {

    @NotBlank(message = "Error message is required")
    @Size(max = 10000, message = "Error text too long - maximum 10000 characters")
    private String errorText;

    //Optional - if provided, vector search will find relevant code
    //if null, LLM gives a generic answer based on error alone
    private UUID repoId;

    //Optional - user can provide additional context about what they are doing
    private String additionalContext;
}
