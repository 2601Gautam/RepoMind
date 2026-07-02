package com.repomind.repomind.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.UUID;

@Data
public class InterviewRequest {

    @NotNull(message = "Repo ID is required")
    private UUID repoId;

    // Validate that difficulty is exactly one of these three values
    // @Pattern on an enum would need a custom validator
    // Using String with pattern is simpler and sufficient here
    @NotBlank(message = "Difficulty is required")
    @Pattern(
            regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
            message = "Difficulty must be BEGINNER, INTERMEDIATE, or ADVANCED"
    )
    private String difficulty;
}