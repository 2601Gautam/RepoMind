package com.repomind.repomind.service;

import java.util.Locale;
import java.util.Optional;

/** Short, standalone social messages need no repository retrieval or model call. */
final class ChatScope {
    private ChatScope() {}

    static Optional<String> reply(String question) {
        String text = question.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?,]+$", "").strip()
                .replaceAll("\\s+", " ");
        return switch (text) {
            case "hi", "hello", "hey", "hello there", "good morning", "good evening" ->
                    Optional.of("Hi! I can help you understand this repository, debug its code, or prepare for a project interview. What would you like to explore?");
            case "thanks", "thank you", "thank u", "thx", "thanks a lot" ->
                    Optional.of("You're welcome! Let me know if you have another question about this repository.");
            case "i love u", "i love you", "love you", "love u", "ily" ->
                    Optional.of("Thanks for the kind words! I'm here to help with this repository's code, debugging, and project interview preparation.");
            case "how are you", "how are u", "what can you do", "who are you" ->
                    Optional.of("I'm RepoMind, your repository assistant. I can explain this project's code, help debug it, and help you prepare for project interviews.");
            default -> Optional.empty();
        };
    }
}
