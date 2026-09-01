package com.repomind.repomind.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GenerateSummary {

    private final ChatClient summaryModel;
    private final PromptBuilder promptBuilder;

    public GenerateSummary(@Qualifier("summaryChatClient") ChatClient summaryModel,PromptBuilder promptBuilder) {
        this.summaryModel = summaryModel;
        this.promptBuilder = promptBuilder;
    }


    public String generateSummary(String userMessage,String chatbotAns,String prevSummary)
    {
        String summaryGeneratePrompt = promptBuilder.rollingSummaryPrompt(prevSummary,userMessage,chatbotAns);

        String output = summaryModel.prompt()
                .user(summaryGeneratePrompt)
                .call()
                .content();

        return output;
    }
}
