package com.repomind.repomind.config;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class AiConfig {

    @Value("${app.models.summary}")
    private String summaryModel;

    @Value("${app.models.chat}")
    private String chatModel;

    @Value("${app.models.reasoning}")
    private String reasoningModel;

    @Value("${app.models.structured}")
    private String structuredModel;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;



    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("openAiChatModel") ChatModel chatmodel)
    {
        return ChatClient.builder(chatmodel)
                .build();
    }

    @Bean("reasoningChatClient")
    public ChatClient reasoningChatClient() {

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(reasoningModel)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .temperature(0.2)
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }

    @Bean("structuredChatClient")
    public ChatClient structuredChatClient() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(structuredModel)
                .temperature(0.1)
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }
    @Bean("summaryChatClient")
    public ChatClient summaryChatClient() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(summaryModel)
                .temperature(0.1)
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }
}