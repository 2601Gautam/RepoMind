package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.ChatRequest;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.model.entity.UserRepo;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.ActivityService;
import com.repomind.repomind.service.ChatService;
import com.repomind.repomind.service.ConversationMemoryService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Leaving the chat page, reloading it or closing the tab must not cancel an answer that is being generated, because the
 * answer is saved only when generation completes. The chat endpoint therefore has to use the detached stream.
 */
class ChatControllerDetachTest {
    @Test void chatAnswersAreNotTiedToTheBrowserConnection() {
        var activity = mock(ActivityService.class);
        var userRepos = mock(UserRepoRepository.class);
        var person = User.builder().id(UUID.randomUUID()).build();
        var repo = UUID.randomUUID();
        when(userRepos.findByUserIdAndRepoId(person.getId(), repo)).thenReturn(Optional.of(new UserRepo()));
        when(activity.streamDetached(any(), any(), any(), any(), any())).thenReturn(Flux.just("{\"type\":\"done\"}"));
        var controller = new ChatController(mock(ChatService.class), activity, mock(ConversationMemoryService.class), userRepos);

        var request = new ChatRequest();
        request.setRepoId(repo);
        request.setMessage("How does login work?");
        var requestId = UUID.randomUUID();
        controller.chat(request, person, requestId);

        verify(activity).streamDetached(eq(requestId), eq(person.getId()), eq(repo), eq("CHAT"), any());
        verify(activity, never()).stream(any(), any(), any(), any(), any());
    }
}
