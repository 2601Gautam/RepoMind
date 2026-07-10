package com.repomind.repomind.service;

import com.repomind.repomind.dto.response.ChatHistoryResponse;
import com.repomind.repomind.model.entity.*;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.ConversationRepository;
import com.repomind.repomind.repository.MessageRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    private final PromptBuilder promptBuilder;
    private final ConversationMemoryService memoryService;

    // Returns Flux<String> for SSE streaming
    // The controller converts this to text/event-stream automatically
    // Each emitted String is one SSE data event
    //
    // Why Flux and not String:
    // String = wait 8 seconds, send everything at once
    // Flux<String> = start sending after 0.5 seconds, word by word
    // The user experience difference is enormous
    public Flux<String> streamChat(UUID repoId, String userQuestion, UUID conversationId, User currentUser){
        // Step 1: Validate repo — do this before any async work
        RepoEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoId));

        if(repo.getStatus() != RepoEntity.IngestionStatus.READY){
            throw new RuntimeException("Repository is not ready. Status: " + repo.getStatus());
        }

        //Step2: Get or create conversation (user-scoped)
        Conversation conversation = resolveConversation(conversationId, repo, currentUser);

        //step3 : Load conversationHistory from redis
        //this is what we gives to LLM "memory" of previous messages
        //without this, every Message is treated as as fresh conversation
        List<ConversationMemoryService.MemoryMessage> history =
                memoryService.getRecentMessages(conversation.getId(),promptBuilder.MEMORY_WINDOW_SIZE);

        log.debug("Loaded {} messages from conversation history", history.size());

        // step4 : Embed question and find relevant code chunks
        String retrievalQuery = buildRetrievalQuery(history, userQuestion);
        float[] questionVector = embeddingService.embed(retrievalQuery);
        String vectorString = embeddingService.toVectorString(questionVector);

        List<CodeChunkRepository.CodeChunkProjection> relevantChunks =
                chunkRepository.findTopSimilarChunks(repoId,vectorString,8);

        if(relevantChunks.isEmpty()){
            // Return a Flux that emits one event and completes
            return buildSingleEventFlux("done", "No relevant code found for your question.",
                    conversation.getId(), List.of());
        }

        String codeContext = relevantChunks.stream()
                .map(CodeChunkRepository.CodeChunkProjection::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        List<String> sourcePaths = relevantChunks.stream()
                .map(CodeChunkRepository.CodeChunkProjection::getFilePath)
                .distinct()
                .toList();

        //step5: Build Prompts with conversation history included
        String systemPrompt = promptBuilder.buildChatSystemPrompt();
        String userPrompt = promptBuilder.buildChatUserPrompt(history,codeContext,userQuestion);

        // Step 6: Save user message to Redis memory immediately
        // Save before LLM call so if LLM fails, we still have the question recorded
        memoryService.addMessage(conversation.getId(), "user", userQuestion);

        // Step 7: Stream the response
        // AtomicReference collects the full response as tokens stream in
        // After streaming completes, we save the full response to Redis and DB
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());
        UUID conversationIdFinal = conversation.getId();

        // Build SSE event stream:
        // 1. First emit conversation ID so frontend knows which conversation this belongs to
        // 2. Then emit each token as it arrives
        // 3. After all tokens, emit sources
        // 4. Finally emit done signal
        Flux<String> tokenSteam = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .doOnNext(token -> fullResponse.get().append(token))
                .map(token-> formatSseEvent("token",token,conversationIdFinal,null))
                .doOnComplete(() -> {
                    // Streaming finished — save complete response to Redis and DB
                    String completeAnswer = fullResponse.get().toString();

                    //save assistant response to Redis memory
                    memoryService.addMessage(conversationIdFinal,"Assistant",completeAnswer);

                    //Save both messages to data base for persistence
                    saveMessageToDB(conversation,userQuestion,completeAnswer,sourcePaths);

                    log.debug("Chat streaming complete. Conversation: {}",conversationIdFinal);
                })
                .doOnError(e -> log.error("Streaming error for conversation {}: {}",conversationIdFinal,e.getMessage()));

        // Prepend the conversation ID event and append sources + done events
        Flux<String> startEvent = Flux.just(
                formatSseEvent("start",conversationIdFinal.toString(),conversationIdFinal,null)
        );

        Flux<String> sourcesEvent = Flux.just(
                formatSseEvent("sources",String.join(",",sourcePaths),conversationIdFinal,null)
        );

        Flux<String> doneEvent = Flux.just(
                formatSseEvent("done","",conversationIdFinal,null)
        );

        // Concatenate: start → tokens → sources → done
        return Flux.concat(startEvent,tokenSteam,sourcesEvent,doneEvent)
                .onErrorResume(e -> {
                    log.error("Chat stream error: {}",e.getMessage());
                    return Flux.just(formatSseEvent("error","Failed to get response. Please try again.",conversationIdFinal,null));
                });
    }

    // Format SSE event as JSON string
    // Frontend parses: {"type":"token","content":"...","conversationId":"..."}
    private String formatSseEvent(String type, String content, UUID conversationId, List<String> sources) {
        // Escape content for JSON — handle quotes and newlines
        String escapedContent = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        if(sources != null){
            String sourcesJson = sources.stream()
                    .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                    .collect(Collectors.joining(",", "[", "]"));
            return "{\"type\":\"" + type + "\",\"sources\":" + sourcesJson
                    + ",\"conversationId\":\"" + conversationId + "\"}";
        }

        return "{\"type\":\"" + type + "\",\"content\":\""
                + escapedContent + "\",\"conversationId\":\"" + conversationId + "\"}";
    }

    private Flux<String> buildSingleEventFlux(String type, String message, UUID conversationId, List<String> sources) {
        return Flux.concat(
                Flux.just(formatSseEvent("start",conversationId.toString(),conversationId,null)),
                Flux.just(formatSseEvent(type,message,conversationId,sources)),
                Flux.just(formatSseEvent("done","",conversationId,null))
        );
    }
    // Folds the last exchange (if any) into the current question before embedding it.
// Cheap, no extra LLM call. For a more accurate fix later, consider an actual
// "condense question" LLM call (rewrite the follow-up into a standalone question
// given the history) — this is the pattern used by most RAG chat implementations.
    private String buildRetrievalQuery(List<ConversationMemoryService.MemoryMessage> history, String userQuestion) {
        if (history.isEmpty()) {
            return userQuestion;
        }
        int fromIndex = Math.max(0, history.size() - 2); // last user+assistant turn
        String recentContext = history.subList(fromIndex, history.size()).stream()
                .map(ConversationMemoryService.MemoryMessage::content)
                .collect(Collectors.joining(" "));
        return recentContext + " " + userQuestion;
    }

    private Conversation resolveConversation(UUID conversationId, RepoEntity repo, User currentUser) {
        if (conversationId == null) {
            return conversationRepository.save(
                    Conversation.builder().repository(repo).user(currentUser).build()
            );
        }
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    private void saveMessageToDB(Conversation conversation,String question,String answer,List<String>sources)
    {
        try {
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role("user")
                    .content(question)
                    .build());
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role("assistant")
                    .content(answer)
                    .sources(String.join(",", sources))
                    .build());
        } catch (Exception e) {
           // Non-critical — log but do not propagate
            // User already received their answer via stream
            log.warn("Failed to persist messages to DB: {}", e.getMessage());
        }
    }

    /**
     * Returns the most recent conversation (and its messages) for a given user+repo pair.
     * Called on page load so the frontend can restore the chat without using localStorage.
     *
     * Strategy:
     *  1. Try the user-scoped query first (conversations saved with user_id — new data).
     *  2. Fall back to the latest conversation for the repo regardless of user_id
     *     (covers old conversations created before user_id was tracked).
     * Returns empty Optional if no conversation/messages exist yet.
     */
    public Optional<ChatHistoryResponse> getChatHistory(UUID repoId, User currentUser) {
        // Step 1: prefer the user-scoped conversation (accurate, new data)
        Optional<Conversation> found = conversationRepository
                .findByRepositoryIdAndUserIdOrderByCreatedAtDesc(repoId, currentUser.getId())
                .stream()
                .findFirst();

        return found.map(conversation -> {
            List<Message> msgs = messageRepository
                    .findByConversationIdOrderByCreatedAtAsc(conversation.getId());

            if (msgs.isEmpty()) return null;   // conversation exists but has no saved messages

            List<ChatHistoryResponse.MessageDto> dtos = msgs.stream()
                    .map(m -> ChatHistoryResponse.MessageDto.builder()
                            .role(m.getRole())
                            .content(m.getContent())
                            .sources(m.getSources())
                            .build())
                    .collect(Collectors.toList());

            return ChatHistoryResponse.builder()
                    .conversationId(conversation.getId())
                    .messages(dtos)
                    .build();
        }).filter(r -> r != null);
    }
}
