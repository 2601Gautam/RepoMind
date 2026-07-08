package com.repomind.repomind;

import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
        // Explicitly stop Spring from creating an OpenAI EmbeddingModel bean
        // We use OpenAI starter ONLY for Groq chat — not for embeddings
        // Without this exclusion, Spring finds two EmbeddingModel beans and
        // picks OpenAI's silently, ignoring Mistral entirely
        OpenAiEmbeddingAutoConfiguration.class
})
@EnableAsync
@EnableAspectJAutoProxy
@EnableCaching
// @EnableAsync is required for @Async to work
// Without it Spring ignores @Async completely
// The method runs synchronously and your HTTP request hangs for 10 minutes
@EnableCaching // activate @Cacheable , @CacheEvict annotations
public class RepomindApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepomindApplication.class, args);
	}

}
