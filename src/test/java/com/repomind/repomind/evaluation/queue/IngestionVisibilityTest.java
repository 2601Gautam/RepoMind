package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.dto.queue.IngestionJobMessage;
import com.repomind.repomind.service.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionVisibilityTest {
    @Test void failedAttemptWaitsForInFlightVisibilityExtensionBeforeReturning() throws Exception {
        var sqs = mock(SqsClient.class);
        var service = mock(IngestionService.class);
        var json = new ObjectMapper();
        var extending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failed = new CountDownLatch(1);
        UUID id = UUID.randomUUID();
        String body = json.writeValueAsString(new IngestionJobMessage(id,"fixture",null));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body(body).receiptHandle("receipt").messageId("fixture").build()).build());
        doAnswer(invocation -> {
            extending.countDown();
            assertTrue(release.await(5,TimeUnit.SECONDS));
            return ChangeMessageVisibilityResponse.builder().build();
        }).when(sqs).changeMessageVisibility(org.mockito.ArgumentMatchers.<Consumer<ChangeMessageVisibilityRequest.Builder>>any());
        doAnswer(invocation -> {
            assertTrue(extending.await(5,TimeUnit.SECONDS));
            failed.countDown();
            throw new IllegalStateException("fixture failed attempt");
        }).when(service).processIngestionJob(id,"fixture",null);
        var consumer = QueueFixture.consumer(sqs,json,"fixture",service,1,1,0);
        var executor = Executors.newSingleThreadExecutor();
        try {
            consumer.pollQueue();
            assertTrue(failed.await(5,TimeUnit.SECONDS));
            var shutdown = executor.submit(consumer::shutdown);
            assertThrows(TimeoutException.class,() -> shutdown.get(200,TimeUnit.MILLISECONDS),
                    "Shutdown must wait for the in-flight visibility callback");
            release.countDown();
            shutdown.get(5,TimeUnit.SECONDS);
            verify(sqs,never()).deleteMessage(any(DeleteMessageRequest.class));
        } finally {
            release.countDown();
            consumer.shutdown();
            executor.shutdownNow();
        }
    }
}
