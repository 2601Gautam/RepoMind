package com.repomind.repomind.service;


import com.repomind.repomind.dto.request.DebugRequest;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DebugService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final CodeChunkRepository chunkRepository;

    // Explicit constructor required because @Qualifier cannot be used
    // with @RequiredArgsConstructor — Lombok does not support field-level qualifiers
    public DebugService(
            @Qualifier("reasoningChatClient") ChatClient chatClient,
            EmbeddingService embeddingService,
            CodeChunkRepository chunkRepository
    ){
        this.chatClient = chatClient;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
    }

    // Returns Flux<String> for SSE streaming
    // Same pattern as ChatService — token by token delivery
    public Flux<String> streamAnalysis(DebugRequest request){

        //FInd relavant code if repo provided
        List<String> relevantFiles = List.of();
        String codeSection = "";

        if(request.getRepoId()!= null){
            float[] errorEmbedding = embeddingService.embed(request.getErrorText());
            String vectorString = embeddingService.toVectorString(errorEmbedding);

            List<CodeChunkRepository.CodeChunkProjection> chunks = chunkRepository
                    .findTopSimilarChunks(request.getRepoId(),vectorString,5);

            if(!chunks.isEmpty()){
                codeSection = """
                        RELEVANT CODE FROM REPOSITORY:
                        %s
                        """.formatted(chunks.stream()
                        .map(CodeChunkRepository.CodeChunkProjection::getContent)
                        .collect(Collectors.joining("\n\n---\n\n"))
                );

                relevantFiles = chunks.stream()
                        .map(CodeChunkRepository.CodeChunkProjection::getFilePath)
                        .distinct()
                        .toList();
            }
        }

        String prompt = buildDebugPrompt(request.getErrorText(), codeSection, request.getAdditionalContext());

        log.info("Streaming debug analysis for error: {}",
                request.getErrorText().substring(0, Math.min(80, request.getErrorText().length())));
        final List<String> finalRelevantFiles = relevantFiles;

        // Stream the response as plain text with section headers
        // Frontend splits on ## headers to render colored sections
        // This works better than JSON for streaming - no parse error on partial content

        Flux<String> tokenStream = chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(token -> formatToken(token));
        log.info("streaming......");
        Flux<String> startEvent = Flux.just(formatEvent("start", ""));
        Flux<String> sourcesEvent = Flux.just(formatSources(finalRelevantFiles));
        Flux<String> doneEvent = Flux.just(formatEvent("done", ""));
        return Flux.concat(startEvent, tokenStream, sourcesEvent, doneEvent)
                .onErrorResume(e -> {
                    log.error("Debug stream error: {}", e.getMessage());
                    return Flux.just(formatEvent("error", "Analysis failed. Please try again."));
                });
    }

    private String buildDebugPrompt(String errorText, String codeSection, String additionalContext){

        String contextSection = (additionalContext != null && !additionalContext.isBlank())
                ? "\nADDITIONAL CONTEXT: " + additionalContext + "\n"
                : "";

        return """
            You are an expert software engineer debugging a technical error.
            Analyze this error thoroughly and provide a structured response.
            
            ERROR:
            %s
            %s%s
            
            Respond in this EXACT format with these EXACT section headers.
            Use markdown for code examples. Be specific and actionable.
            
            ## Root Cause
            [One clear sentence identifying the exact cause]
            
            ## Explanation
            [Plain English explanation of why this error occurs]
            
            ## Suggested Fix
            [Concrete steps with code examples if applicable]
            
            ## Prevention
            [How to prevent this class of error in future]
            """.formatted(errorText,codeSection,contextSection);
    }

    private String formatToken(String token) {
        String escaped = token
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"type\":\"token\",\"content\":\"" + escaped + "\"}";
    }

    private String formatEvent(String type, String content) {
        return "{\"type\":\"" + type + "\",\"content\":\"" + content + "\"}";
    }

    private String formatSources(List<String> files) {
        String filesJson = files.stream()
                .map(f -> "\"" + f.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"type\":\"sources\",\"files\":" + filesJson + "}";
    }
}
