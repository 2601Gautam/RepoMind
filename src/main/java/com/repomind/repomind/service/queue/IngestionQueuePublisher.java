package com.repomind.repomind.service.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.dto.queue.IngestionJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

/** Publishes durable ingestion work for the SQS worker to process. */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionQueuePublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.ingestion-queue-url}")
    private String queueUrl;

    /**
     * Never swallow a publish failure. The controller must not return 202 for
     * a repository job that did not reach durable storage.
     */
    public void enqueue(UUID repoId, String githubUrl, String token) {
        try {
            String body = objectMapper.writeValueAsString(
                    new IngestionJobMessage(repoId, githubUrl, token));

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());

            log.info("Queued ingestion job for repo {}", repoId);
        } catch (Exception e) {
            log.error("Failed to queue ingestion job for repo {}", repoId, e);
            throw new IllegalStateException("Could not queue ingestion job", e);
        }
    }
}
