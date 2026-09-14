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

    @Value("${app.models.generateEmbedding}")
    private String generateEmbeddingModel;

    @Value("${app.models.chat}")
    private String chatModel;

    @Value("${app.models.reasoning}")
    private String reasoningModel;

    @Value("${app.models.structured}")
    private String structuredModel;

    @Value("${spring.ai.openai.api-key-for-think}")
    private String apiKeyForThinking;

    @Value("${spring.ai.openai.api-key-for-fast}")
    private String apiKeyForFast;

    @Value("${spring.ai.openai.base-url-for-think}")
    private String thinkingModelBaseUrl;

    @Value("${spring.ai.openai.base-url-for-fast}")
    private String fastModelBaseUrl;



    @Bean
    @Primary
    public ChatClient chatClient()
    {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatModel)
                .baseUrl(thinkingModelBaseUrl)   // or thinkingModelBaseUrl — whichever backs your default chat client
                .apiKey(apiKeyForThinking)       // or apiKeyForThinking
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }

    @Bean("reasoningChatClient")
    public ChatClient reasoningChatClient() {

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(reasoningModel)
                .baseUrl(thinkingModelBaseUrl)
                .apiKey(apiKeyForThinking)
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
                .baseUrl(thinkingModelBaseUrl)
                .apiKey(apiKeyForThinking)
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
                .baseUrl(fastModelBaseUrl)
                .apiKey(apiKeyForFast)
                .model(summaryModel)
                .temperature(0.1)
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }
    @Bean("generateEmbeddingClient")
    public ChatClient embeddingGenerateClient() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(fastModelBaseUrl)
                .apiKey(apiKeyForFast)
                .model(generateEmbeddingModel)
                .temperature(0.1)
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }
}