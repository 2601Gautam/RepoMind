package com.repomind.repomind.service;

import com.repomind.repomind.model.entity.*;
import com.repomind.repomind.repository.*;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatSocialReplyTest {
    @Test void affectionGetsASavedScopeReplyWithoutRetrievalOrModelCalls() {
        var client = mock(ChatClient.class);
        var embeddings = mock(EmbeddingService.class);
        var chunks = mock(CodeChunkRepository.class);
        var conversations = mock(ConversationRepository.class);
        var messages = mock(MessageRepository.class);
        var repos = mock(RepoJpaRepository.class);
        var memory = mock(ConversationMemoryService.class);
        var query = mock(GenerateEmbeddingQuery.class);
        var summary = mock(GenerateSummary.class);
        var repo = RepoEntity.builder().id(UUID.randomUUID()).status(RepoEntity.IngestionStatus.READY).build();
        var user = User.builder().id(UUID.randomUUID()).build();
        var conversation = Conversation.builder().id(UUID.randomUUID()).repository(repo).user(user).build();
        when(repos.findById(repo.getId())).thenReturn(Optional.of(repo));
        when(conversations.save(any())).thenReturn(conversation);
        var service = new ChatService(client, embeddings, chunks, conversations, messages, repos,
                new PromptBuilder(), memory, query, summary);

        var events = service.streamChat(repo.getId(), "i love u", null, user).collectList().block();

        assertEquals(4, events.size());
        assertTrue(events.get(1).contains("Thanks for the kind words!"));
        assertTrue(events.get(3).contains("done"));
        verify(messages, times(2)).save(any(Message.class));
        verify(memory).addMessage(conversation.getId(), "user", "i love u");
        verifyNoInteractions(client, embeddings, chunks, query, summary);
    }
}
