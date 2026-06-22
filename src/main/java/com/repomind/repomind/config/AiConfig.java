package com.repomind.repomind.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {


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
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }



}
