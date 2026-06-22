package com.repomind.repomind.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    /// Spring AI reads your application.yml block under spring.ai.google.genai.embedding
    // and auto-creates a GoogleGenAiTextEmbeddingModel bean, injected here automatically
    // No custom HTTP code needed — this is what Nomic could never give us
    private final EmbeddingModel embeddingModel;



    public float[] embed(String text) {
        float[] result = embeddingModel.embed(text);

        // IMPORTANT: verify this actually prints 768, not 3072
        // There are known cases across different SDKs where the dimensions
        // parameter gets silently ignored due to gateway/routing quirks —
        // never assume the config worked, always check this log line
        log.debug("Generated embedding with {} dimensions", result.length);
        return result;
    }

    // pgvector needs the vector in this exact string format for the native SQL query:
    // [0.123,-0.456,0.789,...] — no spaces, square brackets, comma separated
    // This is what you pass to CAST(:embedding AS vector) in the SQL query
    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

}
