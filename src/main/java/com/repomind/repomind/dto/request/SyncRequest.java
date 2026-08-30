package com.repomind.repomind.dto.request;

import lombok.Data;

@Data
public class SyncRequest {
    // Optional — only needed if the repo is private
    private String token;
}