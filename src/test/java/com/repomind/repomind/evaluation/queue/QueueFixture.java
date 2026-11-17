package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.S3StorageService;
import com.repomind.repomind.service.ingestion.*;
import com.repomind.repomind.service.queue.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;

@TestConfiguration(proxyBeanMethods = false)
public class QueueFixture {
    static final String ENDPOINT = "http://127.0.0.1:54566";
    static final int DELAY_MS = 250;

    static class Work {
        volatile Runnable beforeClone = () -> {};
        volatile Runnable atSecondFile = () -> {};
    }

    @Bean Work fixtureWork() { return new Work(); }

    @Bean @Primary FileCloneService fixtureClone(Work work) throws Exception {
        FileCloneService clone = mock(FileCloneService.class);
        Path fixture = Path.of("fixture-only");
        when(clone.cloneRepository(anyString(), isNull())).thenAnswer(i -> {
            work.beforeClone.run();
            Thread.sleep(DELAY_MS);
            return fixture;
        });
        when(clone.getHeadCommitSha(fixture)).thenReturn("queue-eval-1");
        when(clone.extractFiles(fixture)).thenReturn(List.of(
                new FileCloneService.ParsedFile("First.java", "class First {}", ".java"),
                new FileCloneService.ParsedFile("Second.java", "class Second {}", ".java")));
        return clone;
    }

    @Bean @Primary EmbeddingService fixtureEmbeddings(Work work) {
        EmbeddingService embeddings = mock(EmbeddingService.class);
        float[] vector = new float[1024];
        vector[0] = 1;
        when(embeddings.embedBatch(anyList())).thenAnswer(i -> {
            List<String> chunks = i.getArgument(0);
            if (chunks.getFirst().contains("Second.java")) work.atSecondFile.run();
            return Collections.nCopies(chunks.size(), vector);
        });
        when(embeddings.embed(anyString())).thenAnswer(i -> {
            String chunk = i.getArgument(0);
            if (chunk.contains("Second.java")) work.atSecondFile.run();
            return vector;
        });
        return embeddings;
    }

    @Bean @Primary S3StorageService fixtureArchive() { return mock(S3StorageService.class); }

    static String url(UUID id) { return "https://example.invalid/queue/" + id; }

    static SqsClient client() {
        return SqsClient.builder().endpointOverride(URI.create(ENDPOINT)).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))).build();
    }

    static IngestionQueuePublisher publisher(SqsClient sqs, ObjectMapper mapper, String url) {
        var publisher = new IngestionQueuePublisher(sqs, mapper);
        ReflectionTestUtils.setField(publisher, "queueUrl", url);
        return publisher;
    }

    static IngestionQueueConsumer consumer(SqsClient sqs, ObjectMapper mapper, String queue,
                                           IngestionService service, int workers, int visibility, int wait) {
        var consumer = new IngestionQueueConsumer(sqs, service, mapper, workers);
        ReflectionTestUtils.setField(consumer, "queueUrl", queue);
        ReflectionTestUtils.setField(consumer, "visibilityTimeoutSeconds", visibility);
        ReflectionTestUtils.setField(consumer, "longPollSeconds", wait);
        ReflectionTestUtils.setField(consumer, "dlqWarningThreshold", 2);
        return consumer;
    }
}
