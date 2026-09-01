package com.repomind.repomind;

import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        // Explicitly stop Spring from creating an OpenAI EmbeddingModel bean
        // We use OpenAI starter ONLY for Groq chat — not for embeddings
        // Without this exclusion, Spring finds two EmbeddingModel beans and
        // picks OpenAI's silently, ignoring Mistral entirely
        OpenAiEmbeddingAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
// @EnableAsync is required for @Async to work
// Without it Spring ignores @Async completely
// The method runs synchronously and your HTTP request hangs for 10 minutes
// @EnableAsync is required for @Async to work (SES welcome email, the /sync
// endpoint's common-case path, and IngestionController's FAILED-retry publish
// all run off the calling thread because of it).
// @EnableScheduling activates IngestionQueueConsumer's SQS long-polling loop —
// without it, ingestion jobs are published to SQS but nothing ever consumes them.
@EnableCaching // activate @Cacheable , @CacheEvict annotations
public class RepomindApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepomindApplication.class, args);
	}

}
