package com.repomind.repomind;

import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
        // Explicitly stop Spring from creating an OpenAI EmbeddingModel bean
        // We use OpenAI starter ONLY for Groq chat — not for embeddings
        // Without this exclusion, Spring finds two EmbeddingModel beans and
        // picks OpenAI's silently, ignoring Mistral entirely
        OpenAiEmbeddingAutoConfiguration.class
})
@EnableAsync
// @EnableAsync is required for @Async to work
// Without it Spring ignores @Async completely
// The method runs synchronously and your HTTP request hangs for 10 minutes
public class RepomindApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepomindApplication.class, args);
	}

}
