package com.repomind.repomind.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GenerateEmbeddingQuery {

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;

    public GenerateEmbeddingQuery(@Qualifier("summaryChatClient") ChatClient chatClient, PromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
    }

    public String generateEmbeddingQueryFromContext(String userQuestion,String recentContext)
    {

        String prompt = promptBuilder.contextualizeQueryPrompt(recentContext,userQuestion);
        String output = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return output;
    }


}
