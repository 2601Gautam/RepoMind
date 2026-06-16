package com.repomind.repomind.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    // @Value reads the NOMIC_API_KEY environment variable you set in IntelliJ
    // and on Render. Never hardcode API keys in code that goes to GitHub.
    @Value("${NOMIC_API_KEY}")
    private String nomicApiKey;

    // Why this class exists:
    // Spring AI auto-creates a ChatModel bean from your yml config
    // (reads GROQ_API_KEY, base-url, model name from application.yml)
    // But it does NOT auto-create a ChatClient bean
    // ChatClient is the fluent API wrapper that makes building prompts clean
    // You declare this @Bean so Spring creates it and you can inject it anywhere
    // ChatModel  = low level, raw API access
    // ChatClient = high level, builder pattern for prompts
    // Always inject ChatClient in your services, not ChatModel directly@Bean
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // EmbeddingModel explicitly points to Nomic API
    // We build this manually instead of using yml config because
    // having two different OpenAI-compatible endpoints (Groq + Nomic)
    // under the same spring.ai.openai block can cause Spring AI to
    // mix up the API keys and base URLs
    // Building it manually here = zero chance of conflict
    @Bean
    public EmbeddingModel embeddingModel() {
        // OpenAiApi here does NOT mean OpenAI the company
        // It means "a client that speaks the OpenAI API format"
        // Nomic uses the same API format, so this client works perfectly
        OpenAiApi nomicApi = new OpenAiApi(
                "https://api-atlas.nomic.ai/v1/embedding/text",
                System.getenv("NOMIC_API_KEY")
        );

        // OpenAiEmbeddingModel is the Spring AI class that handles
        // sending text to the embedding API and parsing the response
        // We tell it to use nomic-embed-text-v1 which produces 768 numbers per input
        // This 768 MUST match vector(768) in your NeonDB schema
        return new OpenAiEmbeddingModel(
                nomicApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("nomic-embed-text-v1")
                        .build()
        );
    }
}
