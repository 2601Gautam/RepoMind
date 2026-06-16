package com.repomind.repomind.service.ingestion;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ChunkingService {

     // Why 500 lines per chunk:
    // nomic-embed-text accepts up to 8192 tokens
    // 500 lines of average code ≈ 600-800 tokens — safely inside the limit
    // Smaller chunks = more specific embeddings = more precise search results
    // If you make chunks too large, the embedding becomes an "average" of too
    // many things and loses specificity
     private static final int MAX_LINES = 500;

    // Why 40 lines of overlap between consecutive chunks:
    // Imagine a method that starts at line 498 of a file
    // Without overlap: lines 1-500 are chunk 0, lines 501-1000 are chunk 1
    // The method is split — neither chunk has the complete method
    // With overlap: chunk 1 starts at line 460 (500 - 40)
    // So the full method appears in chunk 1 with 40 lines of context before it
    private static final int OVERLAP = 40;

    public List<Chunk> chunkFile(String filePath, String content){
        List<Chunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        if(lines.length == 0)return chunks;

        // Small files: return as one single chunk
        // Splitting a 30-line utility class into pieces destroys its context
        // The whole file fits easily within the token limit anyway
        if (lines.length <= MAX_LINES) {
            chunks.add(buildChunk(filePath, lines, 0, lines.length - 1, 0));
            return chunks;
        }

        // Large files: split into overlapping chunks
        int chunkIndex = 0;
        int start = 0;

        while (start < lines.length) {
            int end = Math.min(start + MAX_LINES - 1, lines.length - 1);
            chunks.add(buildChunk(filePath, lines, start, end, chunkIndex));
            chunkIndex++;

            // Move forward by (MAX_LINES - OVERLAP) not by MAX_LINES
            // This creates the overlap: chunk N ends at line X
            // chunk N+1 starts at line X - OVERLAP
            int nextStart = start + MAX_LINES - OVERLAP;
            if (nextStart >= lines.length) break;
            start = nextStart;
        }

        return chunks;
    }

    private Chunk buildChunk(String filePath, String[] lines, int from,int to,int index) {
        // The file path header at the top of every chunk is not optional
        // When 6 chunks of code arrive at the LLM without file context, it
        // cannot tell you which file the authentication code lives in
        // With this header, the LLM can say:
        // "Authentication is handled in src/service/AuthService.java at lines 23-67"
        // That is what makes the chat actually useful
        String header = String.format(
                "File: %s (lines %d-%d)\n\n",
                filePath, from + 1, to + 1
        );
        String body = String.join("\n", Arrays.copyOfRange(lines, from, to + 1));
        return new Chunk(
                header + body,  // full content: header + code
                filePath,       // path stored separately for source display in frontend
                index,
                from + 1,       // 1-indexed line numbers for humans reading the output
                to + 1
        );
    }

        // Java record: immutable, auto-generates constructor and getters
        // content = what gets embedded (header + code)
        // filePath = what gets shown to user as "source file"
        public record Chunk(
                String content,
                String filePath,
                int chunkIndex,
                int startLine,
                int endLine
        ) {}
}
