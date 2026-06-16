package com.repomind.repomind.service;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.Conversation;
import com.repomind.repomind.model.entity.Message;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.ConversationRepository;
import com.repomind.repomind.repository.MessageRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final CodeChunkRepository chunkRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RepoJpaRepository repoRepository;

    public ChatResult chat(UUID repoId, String userQuestion, UUID conversationId){

        // ── STEP 1: Verify repo exists and is ready ──────────────────────────
        // If someone calls /api/chat before ingestion finishes, give a clear error
        // instead of returning a confusing empty answer
        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoId));

        if (repo.getStatus() != RepoEntity.IngestionStatus.READY) {
            throw new RuntimeException(
                    "Repository is not ready yet. Current status: " + repo.getStatus()
            );
        }

        // ── STEP 2: Get or create conversation ───────────────────────────────
        // conversationId is null on the first message — create a new conversation
        // conversationId is provided on subsequent messages — load the existing one
        // This groups all messages in one session together

        Conversation conversation;
        try {
            if (conversationId == null) {
                conversation = conversationRepository.save(
                        Conversation.builder().repository(repo).build()
                );
            } else {
                conversation = conversationRepository.findById(conversationId)
                        .orElseThrow(() -> new RuntimeException("Conversation not found"));
            }
        } catch (Exception e) {
            log.error("Failed to create or load conversation: {}", e.getMessage());
            throw new RuntimeException("Conversation error: " + e.getMessage());
        }

        // ── STEP 3: Embed the user's question ───────────────────────────────
        // "How does authentication work?" → [0.23, -0.87, 0.14, ...] 768 numbers
        // These numbers represent the MEANING of the question in vector space
        // Similar questions will produce numerically similar vectors
        float[] questionVector;
        try {
            questionVector = embeddingService.embed(userQuestion);
        } catch (Exception e) {
            // If Nomic API is down or rate limited, return a clear message
            // instead of crashing with a 500 error
            log.error("Embedding failed for question: {}", e.getMessage());
            return new ChatResult(
                    "Failed to process your question. Please try again.",
                    List.of(),
                    conversation.getId()
            );
        }

        String vectorString = embeddingService.toVectorString(questionVector);

        // ── STEP 4: Find the 6 most relevant code chunks ────────────────────
        // pgvector compares questionVector against every chunk's stored embedding
        // Returns chunks whose embeddings are closest to the question's embedding
        // "Closest" means most semantically similar — not keyword matching
        //
        // Example: question = "where is login handled"
        // pgvector finds chunks from AuthService.java even if they never
        // contain the word "login" — because their meaning is similar
        List<CodeChunk> relevantChunks;
        try {
            relevantChunks = chunkRepository.findTopSimilarChunks(
                    repoId.toString(),
                    vectorString,
                    6
            );
        } catch (Exception e) {
            // NeonDB might be waking up from sleep or connection dropped
            log.error("Vector search failed: {}", e.getMessage());
            return new ChatResult(
                    "Failed to search the codebase. Please try again.",
                    List.of(),
                    conversation.getId()
            );
        }

        if (relevantChunks.isEmpty()) {
            return new ChatResult(
                    "No relevant code found for your question.",
                    List.of(),
                    conversation.getId()
            );
        }

        // ── STEP 5: Build context string from retrieved chunks ───────────────
        // Join all 6 chunks into one string separated by a divider
        // Each chunk already starts with "File: path/to/File.java (lines X-Y)"
        // because ChunkingService added that header — so the LLM knows
        // exactly which file and lines it is reading
        String codeContext = relevantChunks.stream()
                .map(CodeChunk::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Collect unique file paths to show as "sources" in the frontend
        List<String> sourcePaths = relevantChunks.stream()
                .map(CodeChunk::getFilePath)
                .distinct()
                .toList();

        // ── STEP 6: Build the prompt ─────────────────────────────────────────
        // System prompt: sets the LLM's role and constraints
        // Telling it to ONLY use provided code prevents hallucination
        // Without this constraint, LLMs make up plausible-sounding answers
        String systemPrompt = """
            You are an expert software engineer helping developers understand a codebase.
            
            Rules you must follow:
            1. Answer ONLY based on the code provided in the context below
            2. Always mention specific file names when referring to code
            3. If the answer is not visible in the provided code, say exactly:
               "This is not covered in the provided code context."
            4. Format code examples using markdown code blocks with the language name
            5. Be specific and concise — developers prefer precision over length
            """;

        // User prompt: actual code context + the question
        // The LLM reads the 6 code chunks and answers based on them
        String userPrompt = """
            CODE CONTEXT FROM REPOSITORY:
            
            %s
            
            QUESTION: %s
            
            Answer based strictly on the code shown above.
            """.formatted(codeContext, userQuestion);

        // ── STEP 7: Call Groq via Spring AI ─────────────────────────────────
        // ChatClient sends POST to https://api.groq.com/openai/v1/chat/completions
        // With your prompt and model name from application.yml
        // .content() extracts the plain text response string
        String answer;
        try {
            log.debug("Calling Groq with {} relevant chunks for: {}", relevantChunks.size(), userQuestion);
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            // Groq API down, rate limited, or response parsing failed
            log.error("LLM call failed: {}", e.getMessage());
            return new ChatResult(
                    "Failed to get an answer from the AI. Please try again.",
                    sourcePaths,
                    conversation.getId()
            );
        }

        // ── STEP 8: Save both messages to DB ────────────────────────────────
        // Save user's question first, then assistant's answer
        // If this fails we still return the answer — saving history is not critical
        // The user gets their answer even if DB write fails
        try {
            messageRepository.save(
                    Message.builder()
                            .conversation(conversation)
                            .role("user")
                            .content(userQuestion)
                            .build()
            );

            messageRepository.save(
                    Message.builder()
                            .conversation(conversation)
                            .role("assistant")
                            .content(answer)
                            // Store source paths as comma-separated string
                            .sources(String.join(",", sourcePaths))
                            .build()
            );
        } catch (Exception e) {
            // Non-critical: log the error but still return the answer
            // Losing chat history is acceptable, losing the answer is not
            log.warn("Failed to save messages to DB: {}", e.getMessage());
        }

        return new ChatResult(answer, sourcePaths, conversation.getId());
    }
    public record ChatResult(String answer, List<String> sources, UUID conversationId) {}
}
