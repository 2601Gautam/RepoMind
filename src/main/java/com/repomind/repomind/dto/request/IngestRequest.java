package com.repomind.repomind.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;


@Data
public class IngestRequest {
    // @NotBlank: Spring Validation checks this before your method even runs
    // If githubUrl is null or empty, Spring returns 400 Bad Request automatically
    // Your controller method never gets called — validation stops it first
    @NotBlank(message = "GitHub URL is required")
    @Pattern(
            regexp = "https://github\\.com/[\\w./-]+",
            message = "Must be a valid GitHub URL like https://github.com/user/repo"
    )
    private String githubUrl;
    // Optional — only needed for private repos
    // User generates this from GitHub Settings → Developer Settings → Personal Access Tokens
    private String token;
}
