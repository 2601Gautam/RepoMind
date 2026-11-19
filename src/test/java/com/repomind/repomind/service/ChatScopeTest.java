package com.repomind.repomind.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ChatScopeTest {
    @ParameterizedTest
    @ValueSource(strings = {"i love u", "I LOVE YOU!!!", "  hi  ", "Thank you.", "how are you?", "what can you do"})
    void standaloneSocialMessagesGetARepositoryRedirect(String message) {
        var reply = ChatScope.reply(message).orElseThrow();
        assertFalse(reply.contains("not covered"));
        assertTrue(reply.contains("repository") || reply.contains("project"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hi, how does login work?", "Why does the UI display 'i love u'?", "Explain the hello function", "What about its tests?", "i love u; explain AuthController"})
    void technicalAndMixedMessagesStillReachRepositoryRetrieval(String message) {
        assertTrue(ChatScope.reply(message).isEmpty());
    }
}
