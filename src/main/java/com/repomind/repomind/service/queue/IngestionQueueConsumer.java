package com.repomind.repomind.service.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.dto.queue.IngestionJobMessage;
import com.repomind.repomind.service.ingestion.IngestionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Long-polls the SQS ingestion queue. A message is deleted only after the
 * service returns normally, which means the repository reached READY or a
 * durably recorded FAILED state. A process crash leaves the message for SQS
 * to redeliver after its visibility timeout.
 */
@Component
@ConditionalOnProperty(name = "aws.sqs.consumer.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class IngestionQueueConsumer {

    private static final int WORKER_COUNT = 4;

    private final SqsClient sqsClient;
    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(WORKER_COUNT);
    private final Semaphore workerSlots = new Semaphore(WORKER_COUNT);

    @Value("${aws.sqs.ingestion-queue-url}")
    private String queueUrl;

    @Value("${aws.sqs.long-poll-seconds:20}")
    private int longPollSeconds;

    @Value("${aws.sqs.visibility-timeout-seconds:1200}")
    private int visibilityTimeoutSeconds;

    public IngestionQueueConsumer(
            SqsClient sqsClient,
            IngestionService ingestionService,
            ObjectMapper objectMapper
    ) {
        this.sqsClient = sqsClient;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${aws.sqs.poll-delay-ms:1000}")
    public void pollQueue() {
        int availableWorkers = workerSlots.availablePermits();
        if (availableWorkers == 0) {
            return;
        }

        try {
            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(Math.min(availableWorkers, 10))
                    .waitTimeSeconds(longPollSeconds)
                    .visibilityTimeout(visibilityTimeoutSeconds)
                    .build()).messages();

            for (Message message : messages) {
                if (!workerSlots.tryAcquire()) {
                    // This can only occur if scheduler configuration changes;
                    // leave the message in SQS rather than queueing it locally.
                    return;
                }
                try {
                    workerPool.submit(() -> {
                        try {
                            processMessage(message);
                        } finally {
                            workerSlots.release();
                        }
                    });
                } catch (RuntimeException e) {
                    workerSlots.release();
                    throw e;
                }
            }
        } catch (Exception e) {
            // A receive failure must not kill the scheduler. No message was
            // acknowledged, so SQS will retain any work for a later poll.
            log.error("Could not poll the ingestion SQS queue", e);
        }
    }

    private void processMessage(Message message) {
        try {
            IngestionJobMessage job = objectMapper.readValue(message.body(), IngestionJobMessage.class);
            log.info("Picked up ingestion job for repo {} from SQS", job.repoId());
            ingestionService.processIngestionJob(job.repoId(), job.githubUrl(), job.token());
        } catch (Exception e) {
            // Do not delete malformed or unexpectedly failed jobs. Configure a
            // dead-letter queue so SQS retries them a bounded number of times.
            log.error("Ingestion job failed for SQS message {}. Leaving it for retry.",
                    message.messageId(), e);
            return;
        }

        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            // The database state is already terminal. A later duplicate
            // delivery is harmless because IngestionService claims only
            // PENDING or expired PROCESSING jobs.
            log.error("Could not acknowledge completed SQS message {}", message.messageId(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
