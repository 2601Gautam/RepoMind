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

    // The openai-java SDK that Spring AI 2.0 wraps defaults to a 10-minute call/read
    // timeout. Fine for a stable provider, but OpenRouter's free models are best-effort
    // capacity and can queue or stall well past what a chat UI should ever wait on.
    // We cap it explicitly so a stuck request fails fast instead of hanging silently.
    @Value("${app.http.timeout-seconds:90}")
    private long httpTimeoutSeconds;

    @Value("${app.http.reasoning-timeout-seconds:300}")
    private long reasoningTimeoutSeconds;

    private org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer timeoutCustomizer(Duration timeout) {
        return builder -> builder.timeout(timeout);
    }

    // Applied automatically to the auto-configured OpenAiChatModel bean that
    // @Qualifier("openAiChatModel") resolves to below - Spring AI picks up any bean
    // of this type when it builds that model.
    @Bean
    public OpenAiHttpClientBuilderCustomizer defaultOpenAiTimeoutCustomizer() {
        return timeoutCustomizer(Duration.ofSeconds(httpTimeoutSeconds));
    }

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
                // reasoning models are genuinely slower - give them more room than
                // chat, but still well under the SDK's 10-minute default
                .httpClientBuilderCustomizer(timeoutCustomizer(Duration.ofSeconds(reasoningTimeoutSeconds)))
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
                .httpClientBuilderCustomizer(timeoutCustomizer(Duration.ofSeconds(httpTimeoutSeconds)))
                .build();

        return ChatClient.builder(model).build();
    }
}