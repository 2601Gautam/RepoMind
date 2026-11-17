package com.repomind.repomind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StreamJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    private StreamJson() {}

    static String encode(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not encode response", e); }
    }

    static String chat(String type, String content, UUID conversation) {
        return encode(Map.of("type",type,"content",content,"conversationId",conversation));
    }

    static String sources(List<String> paths, UUID conversation) {
        // Older clients read content; new clients use the lossless source paths.
        return encode(Map.of("type","sources","content",String.join(",",paths),"sources",paths,"conversationId",conversation));
    }

    static List<String> storedPaths(String json, String legacy) {
        if (json != null) {
            try { return List.of(JSON.readValue(json, String[].class)); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored source paths",e); }
        }
        return legacy == null || legacy.isEmpty() ? List.of() : List.of(legacy.split(","));
    }
}

