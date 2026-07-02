package com.repomind.repomind.service;


import com.repomind.repomind.dto.request.DebugRequest;
import com.repomind.repomind.dto.response.DebugResponse;
import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebugService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final CodeChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    public DebugResponse analyzeError(DebugRequest request){
        //find relevant code if a repo was provided
        //this is the same RAG pattern from tier1 chat
        //The error message itseld becomes the search query
        String relevantCode = "";
        List<String> relevantFiles = List.of();

        if(request.getRepoId() != null) {
            // Embed the error text to find semantically similar code chunks
            // "NullPointerException in UserService" will match UserService.java chunks
            float[] errorEmbedding = embeddingService.embed(request.getErrorText());
            String vectorString = embeddingService.toVectorString(errorEmbedding);

            List<CodeChunkRepository.CodeChunkProjection> chunks = chunkRepository.findTopSimilarChunks(
                    request.getRepoId(),
                    vectorString,
                    5   // top 5 relevant chunks — fewer than chat because error analysis
                    // needs focused context, not broad context
            );

            if (!chunks.isEmpty()) {
                relevantCode = chunks.stream()
                        .map(CodeChunkRepository.CodeChunkProjection::getContent)
                        .collect(Collectors.joining("\n\n---\n\n"));

                relevantFiles = chunks.stream()
                        .map(CodeChunkRepository.CodeChunkProjection::getFilePath)
                        .distinct()
                        .toList();

                log.debug("Found {} relevant chunks for error analysis,", chunks.size());
            }
        }

            //Build The Prompt
            String prompt = buildDebugPrompt(
                    request.getErrorText(),
                    relevantCode,
                    request.getAdditionalContext()
            );

            log.info("Analyzing error: {}",
                    request.getErrorText().substring(0,Math.min(100, request.getErrorText().length())));

            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseDebugResponse(rawResponse,relevantFiles);
    }
    private String buildDebugPrompt(String errorText, String relevantCode, String additionalContext){

        String codeSection = relevantCode.isBlank() ? "" : """
                RELEVANT CODE FROM THE REPOSITORY:
                %s
                """.formatted(relevantCode);

        String contextSection = (additionalContext != null && !additionalContext.isBlank())
        ? "ADDITIONAL CONTEXT: " + additionalContext + "\n\n"
        : "";

        return """
                You are an expert software engineer debugging a technical error.
                 
                ERROR TO ANALYZE:
                %s
                
                %s%s
                
                analyze this error and provide:
                1. The root in one clear sentence
                2. A plain English explanation of why this error occurs
                3. A concrete fix - what to change and where (include code if relevant)
                4. How to prevent this types of error in future
                
                CRITICAL: Respond with ONLY valid JSON. No markdown. No explanation outside the JSON.
                Start with { and end with }.
                
                Use exactly this structure:
                            {
                              "rootCause": "One sentence identifying the exact cause",
                              "explanation": "Plain English explanation of why this error happens",
                              "suggestedFix": "Concrete steps to fix it, with code examples if helpful",
                              "preventionTip": "How to prevent this class of error in future"
                            }
                """.formatted(errorText,codeSection,contextSection);
    }

    private DebugResponse parseDebugResponse(String rawResponse, List<String> relevantFiles){
        try{
            //clean llm formating
            String cleaned = rawResponse
                    .replaceAll("(?s)```json\\*","")
                    .replaceAll("(?s)```\\s*","")
                    .trim();

            //find JSON object boundaries
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");

            if(start==-1 || end == -1)
            {
                // LLM failed to follow JSON format — return a fallback response
                // using the raw text as the explanation rather than crashing
                log.warn("LLM did not return valid JSON for debug response");
                return DebugResponse.builder()
                        .rootCause("Unable To parse structured response")
                        .explanation(cleaned)
                        .suggestedFix("Please try again")
                        .preventionTip("")
                        .relevantFiles(relevantFiles)
                        .build();
            }

            String jsonOnly = cleaned.substring(start,end + 1);
//            readTree() parses the JSON string and creates a tree structure.
            JsonNode node = objectMapper.readTree(jsonOnly);

            return DebugResponse.builder()
                    .rootCause(node.path("rootCause").asText(""))
                    .explanation(node.path("explanation").asText(""))
                    .suggestedFix(node.path("suggestedFix").asText(""))
                    .preventionTip(node.path("preventionTip").asText(""))
                    .relevantFiles(relevantFiles)
                    .build();
        } catch (Exception e) {
           log.error("Failed to parse debug response: {}", e.getMessage());
           //return the raw response as explanation rather than throwing
            // Better UX: user gets something userfull even if parsing fails

            return DebugResponse.builder()
                    .rootCause("Error analysis completed")
                    .explanation(rawResponse)
                    .suggestedFix("")
                    .preventionTip("")
                    .relevantFiles(relevantFiles)
                    .build();
        }
    }
}
