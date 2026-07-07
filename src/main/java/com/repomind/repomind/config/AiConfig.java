package com.repomind.repomind.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class AiConfig {

    //model names come from application.yml -> envrionment variables
    //changing model requires only render ing the application.yml file, zero code change
    //this follows the open/closed principle of software design - open for extension, closed for modification

    @Value("${app.models.chat}")
    private String chatModel;

    @Value("${app.models.reasoning}")
    private String reasoningModel;

    @Value("${app.models.structured}")
    private String structuredModel;

    @Value("${spring.ai.openai.api-key}")
    private String groqApiKey;

    @Value("${spring.ai.openai.base-url}")
    private String groqBaseUrl;

    //primary ChatClient - used by chatService for RAG chat
    //fast model, optimized for high volume conversational queries
    //@primary means this is injected when no @Qualifier is specified
    //your existing code that uses ChatClient will continue to work without any changes
    //will automatically use this bean - no changes needed in those classes

    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("openAiChatModel") ChatModel chatmodel)
    {
        //spring auto-creates chatmodel from application.yml -> envrionment variables
        //we wrap it in chatclient for the fluent prompt-building API
        return ChatClient.builder(chatmodel)
                .build();
    }
    // Reasoning ChatClient — used by DebugService
    // Smarter model, slower, more expensive — justified for error analysis
    // where accuracy matters more than speed
    // Inject with: @Qualifier("reasoningChatClient")
    @Bean("reasoningChatClient")
    public ChatClient reasoningChatClient() {
        // We create a separate OpenAiChatModel pointing to the same Groq API
        // but with a different model name
        // This is the Strategy pattern — same interface, different behavior

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(reasoningModel)
                .baseUrl(groqBaseUrl)
                .apiKey(groqApiKey)
                .temperature(0.2) // lower temperature for more deterministic reasoning
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }
    // Structured ChatClient — used by InterviewService
    // Lower temperature for consistent structured output
    // Inject with: @Qualifier("structuredChatClient")
    @Bean("structuredChatClient")
    public ChatClient structuredChatClient() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(groqBaseUrl)
                .apiKey(groqApiKey)
                .model(structuredModel)
                .temperature(0.1)  // Very low temperature — want deterministic JSON output
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(model).build();
    }
}
