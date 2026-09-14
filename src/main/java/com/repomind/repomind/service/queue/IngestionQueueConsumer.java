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
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
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
 * <p>
 * A message that keeps failing is never deleted here, so a queue-level
 * redrive policy (configured on the SQS queue itself, not in this class —
 * see SETUP_AWS.md) moves it to the dead-letter queue after its configured
 * max receive count. This class does not need to know the DLQ exists for
 * that to work; it only logs the approximate receive count so a message
 * about to be dead-lettered shows up in application logs instead of only
 * being visible from the AWS Console.
 */
@Component
@ConditionalOnProperty(name = "aws.sqs.consumer.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class IngestionQueueConsumer {

    private final int workerCount;
//
    private final SqsClient sqsClient;
    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool;
    private final Semaphore workerSlots;

    @Value("${aws.sqs.ingestion-queue-url}")
    private String queueUrl;

    @Value("${aws.sqs.long-poll-seconds:20}")
    private int longPollSeconds;

    @Value("${aws.sqs.visibility-timeout-seconds:1200}")
    private int visibilityTimeoutSeconds;

    // Log a warning once a message's receive count reaches this many fewer
    // than the queue's configured maxReceiveCount, so an operator sees it
    // before it silently lands in the DLQ. Set this at or below whatever
    // maxReceiveCount you configured on the queue's redrive policy.
    @Value("${aws.sqs.dlq-warning-threshold:2}")
    private int dlqWarningThreshold;

    public IngestionQueueConsumer(
            SqsClient sqsClient,
            IngestionService ingestionService,
            ObjectMapper objectMapper,
            @Value("${app.worker-count:4}") int workerCount
    ) {
        this.sqsClient = sqsClient;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.workerCount = workerCount;
        this.workerPool = Executors.newFixedThreadPool(workerCount);
        this.workerSlots = new Semaphore(workerCount);
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
                    // Ask SQS for the receive count so a message nearing its
                    // maxReceiveCount (and therefore about to be moved to the
                    // DLQ) can be flagged in logs rather than discovered only
                    // by noticing it in the DLQ afterward.
                    .attributeNamesWithStrings(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT.toString())
                    .build()).messages();

            for (Message message : messages) {
                logIfNearDlq(message);

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

    /**
     * Best-effort visibility only — never throws, never blocks processing.
     * SQS enforces the actual redrive policy regardless of what happens here.
     */
    private void logIfNearDlq(Message message) {
        try {
            String raw = message.attributes()
                    .get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
            if (raw == null) {
                return;
            }
            int receiveCount = Integer.parseInt(raw);
            if (receiveCount >= dlqWarningThreshold) {
                log.warn("SQS message {} has been received {} times and is approaching its " +
                                "redrive-policy maxReceiveCount — check the queue's dead-letter " +
                                "queue if this keeps climbing.",
                        message.messageId(), receiveCount);
            }
        } catch (Exception e) {
            log.debug("Could not read ApproximateReceiveCount for message {}: {}",
                    message.messageId(), e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            IngestionJobMessage job = objectMapper.readValue(message.body(), IngestionJobMessage.class);
            log.info("Picked up ingestion job for repo {} from SQS", job.repoId());
            ingestionService.processIngestionJob(job.repoId(), job.githubUrl(), job.token());
        } catch (Exception e) {
            // Do not delete malformed or unexpectedly failed jobs. The
            // queue's redrive policy (configured on the SQS queue itself,
            // per SETUP_AWS.md) moves a message here to the dead-letter
            // queue after its maxReceiveCount is exceeded — no code change
            // is needed on this side for that to happen.
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