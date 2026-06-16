package com.repomind.repomind.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    // EmbeddingModel is a Spring AI interface
    // Spring looks at your active profile and auto-injects the right implementation:
    // local profile & prod profile   → OpenAiEmbeddingModel (calls Nomic API)
    // This class never changes between environments — only the injected implementation changes
    private final EmbeddingModel embeddingModel;

    public float[] embed(String text) {
        // embeddingModel.embed() makes an HTTP call to OpenAi
        // OpenAi runs nomic-embed-text on the text
        // Returns List<Double> of exactly 768 numbers
        // These numbers represent the "meaning" of the text in vector space
        log.debug("Embedding text of length: {}", text.length());

        try{
            float[] result = embeddingModel.embed(text);
            log.debug("Generated embedding with {} dimensions", result.length);
            return result;
        }catch (Exception e){
            log.error("Nomic API call failed: {}", e.getMessage());
            throw new RuntimeException("Embedding failed: " + e.getMessage());
        }
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
