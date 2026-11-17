package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ingestion.IngestionService;
import com.repomind.repomind.service.ingestion.FullIngestionStore;
import com.repomind.repomind.model.entity.RepoEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "ingestion.embedding-batch-size=1")
@ActiveProfiles("eval") @Import(QueueFixture.class)
class QueueRecoveryIT {
    @Autowired JdbcTemplate db;
    @Autowired IngestionService service;
    @Autowired ObjectMapper json;
    @Autowired FullIngestionStore store;
    @Autowired QueueFixture.Work work;
    SqsClient sqs;
    String queue;
    UUID id = UUID.randomUUID();

    @BeforeEach void setup() {
        assertEquals("repomind_eval", db.queryForObject("select current_database()", String.class));
        sqs = QueueFixture.client();
        queue = sqs.createQueue(b -> b.queueName("qa-" + id)).queueUrl();
        db.update("insert into repositories(id,github_url,status) values(?,?,'PENDING')", id, QueueFixture.url(id));
    }
    @AfterEach void clean() {
        if (queue != null) sqs.deleteQueue(b -> b.queueUrl(queue));
        if (sqs != null) sqs.close();
        db.update("delete from repositories where id=?",id);
    }
    void send() { QueueFixture.publisher(sqs,json,queue).enqueue(id,QueueFixture.url(id),null); }
    void processOne() {
        var worker = QueueFixture.consumer(sqs,json,queue,service,1,1,0);
        try { worker.pollQueue(); } finally { worker.shutdown(); }
    }
    List<String> files() {
        return db.queryForList("select file_path from code_chunks where repo_id=? order by file_path",String.class,id);
    }

    @Test void unexpiredLeaseKeepsTheOnlyRedeliveryAvailable() throws Exception {
        db.update("update repositories set status='PROCESSING', ingestion_lease_until=localtimestamp+interval '3 seconds' where id=?",id);
        send();
        processOne();
        Thread.sleep(4050);
        assertEquals(1, sqs.receiveMessage(b -> b.queueUrl(queue).waitTimeSeconds(1)).messages().size(),
                "a busy database lease is not a terminal outcome");
        assertTrue(files().isEmpty());
    }

    @Test void expiredPartialIndexRetriesCleanlyAndTerminalDuplicateDoesNoWork() throws Exception {
        db.update("update repositories set status='PROCESSING', ingestion_lease_until=localtimestamp-interval '1 minute' where id=?",id);
        db.update("insert into code_chunks(repo_id, file_path,chunk_index,content) values(?,'First.java',0,'obsolete partial data')",id);
        send(); processOne();
        assertEquals("READY",db.queryForObject("select status from repositories where id=?",String.class,id));
        assertEquals(List.of("First.java","Second.java"),files());
        var originalIds = db.queryForList("select id from code_chunks where repo_id=? order by id",UUID.class,id);
        send(); processOne();
        assertEquals(originalIds,db.queryForList("select id from code_chunks where repo_id=? order by id",UUID.class,id));
        Thread.sleep(1200);
        assertEquals(0, sqs.receiveMessage(b -> b.queueUrl(queue)).messages().size());
    }

    @Test void invalidMessageReachesConfiguredDeadLetterQueue() throws Exception {
        String dead = sqs.createQueue(b -> b.queueName("dead-" + id)).queueUrl();
        try {
            String arn = sqs.getQueueAttributes(b -> b.queueUrl(dead).attributeNames(QueueAttributeName.QUEUE_ARN))
                    .attributes().get(QueueAttributeName.QUEUE_ARN);
            String policy = json.writeValueAsString(Map.of("deadLetterTargetArn",arn,"maxReceiveCount","2"));
            sqs.setQueueAttributes(b -> b.queueUrl(queue).attributes(Map.of(QueueAttributeName.REDRIVE_POLICY,policy)));
            sqs.sendMessage(b -> b.queueUrl(queue).messageBody("invalid fixture"));
            String recovered = null;
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
            while (recovered == null && System.nanoTime() < deadline) {
                processOne();
                var messages = sqs.receiveMessage(b -> b.queueUrl(dead)).messages();
                if (!messages.isEmpty()) recovered = messages.getFirst().body();
                else Thread.sleep(250);
            }
            assertEquals("invalid fixture", recovered);
            assertTrue(files().isEmpty());
        } finally { sqs.deleteQueue(b -> b.queueUrl(dead)); }
    }

    @Test void publishFailurePropagatesToTheCaller() {
        var publisher = QueueFixture.publisher(sqs,json,queue + "-missing");
        assertThrows(IllegalStateException.class, () -> publisher.enqueue(id,QueueFixture.url(id),null));
    }

    @Test void supersededWorkerCannotAppendOrOverwriteNewOwner() {
        var old = store.claim(id,60).orElseThrow();
        var oldOwner = old.getIngestionOwner();
        db.update("update repositories set ingestion_lease_until=localtimestamp-interval '1 minute' where id=?",id);
        var replacement = store.claim(id,120).orElseThrow();
        assertNotEquals(oldOwner,replacement.getIngestionOwner());
        assertThrows(IllegalStateException.class, () -> store.renew(id,oldOwner,120));
        assertThrows(IllegalStateException.class, () -> store.releaseForRetry(id,oldOwner,"stale error"));
        assertThrows(IllegalStateException.class, () -> store.appendBatch(id,oldOwner,List.of()));
        old.setStatus(RepoEntity.IngestionStatus.FAILED);
        assertThrows(IllegalStateException.class, () -> store.saveProgress(old,oldOwner));
        assertEquals("PROCESSING",db.queryForObject("select status from repositories where id=?",String.class,id));
    }

    @Test void progressPreservesRenewedLeaseAndCompletionClearsOwnership() {
        var claimed = store.claim(id,60).orElseThrow();
        var owner = claimed.getIngestionOwner();
        store.renew(id,owner,120);
        var renewed = db.queryForObject("select ingestion_lease_until from repositories where id=?",java.time.LocalDateTime.class,id);
        store.saveProgress(claimed,owner);
        assertEquals(renewed,db.queryForObject("select ingestion_lease_until from repositories where id=?",java.time.LocalDateTime.class,id));
        assertEquals(owner,db.queryForObject("select ingestion_owner from repositories where id=?",UUID.class,id));
        claimed.setStatus(RepoEntity.IngestionStatus.READY);
        store.saveProgress(claimed,owner);
        assertNull(db.queryForObject("select ingestion_owner from repositories where id=?",UUID.class,id));
        assertNull(db.queryForObject("select ingestion_lease_until from repositories where id=?",java.time.LocalDateTime.class,id));
        assertThrows(IllegalStateException.class, () -> store.renew(id,owner,120));
    }

    @Test void expiredOwnerCannotRenewItselfEvenBeforeTakeover() {
        var claimed = store.claim(id,60).orElseThrow();
        db.update("update repositories set ingestion_lease_until=localtimestamp-interval '1 minute' where id=?",id);
        assertThrows(IllegalStateException.class, () -> store.renew(id,claimed.getIngestionOwner(),60));
        assertThrows(IllegalStateException.class, () -> store.appendBatch(id,claimed.getIngestionOwner(),List.of()));
    }

    @Test void cloneFailureIsRetainedForRetryAndThenCompletes() throws Exception {
        work.beforeClone = () -> { throw new IllegalStateException("fixture clone failed"); };
        try {
            send(); processOne();
            assertEquals("PENDING",db.queryForObject("select status from repositories where id=?",String.class,id));
            assertEquals("fixture clone failed",db.queryForObject("select error_message from repositories where id=?",String.class,id));
            Thread.sleep(1200);
            // A short poll against LocalStack can return nothing although the message is visible; a bounded long poll cannot.
            assertEquals(1,sqs.receiveMessage(b -> b.queueUrl(queue).visibilityTimeout(0).waitTimeSeconds(3)).messages().size());
            work.beforeClone = () -> {};
            processOne();
            assertEquals("READY",db.queryForObject("select status from repositories where id=?",String.class,id));
            assertEquals(List.of("First.java","Second.java"),files());
        } finally { work.beforeClone = () -> {}; }
    }

    @Test void lostAcknowledgementRedeliversWithoutRepeatingCommittedWrites() throws Exception {
        var client = org.mockito.Mockito.mock(SqsClient.class, org.mockito.AdditionalAnswers.delegatesTo(sqs));
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated acknowledgement failure"))
                .when(client).deleteMessage(org.mockito.ArgumentMatchers.any(DeleteMessageRequest.class));
        send();
        var first = QueueFixture.consumer(client,json,queue,service,1,1,0);
        try { first.pollQueue(); } finally { first.shutdown(); }
        var committed = db.queryForList("select id from code_chunks where repo_id=? order by id", UUID.class,id);
        assertEquals(2,committed.size());
        Thread.sleep(1200);
        var redelivery = sqs.receiveMessage(b -> b.queueUrl(queue).attributeNamesWithStrings("ApproximateReceiveCount")
                .visibilityTimeout(0).waitTimeSeconds(3)).messages();
        assertEquals(1,redelivery.size());
        assertEquals("2",redelivery.getFirst().attributesAsStrings().get("ApproximateReceiveCount"));
        processOne();
        Thread.sleep(1200);
        assertTrue(sqs.receiveMessage(b -> b.queueUrl(queue)).messages().isEmpty());
        assertEquals(committed,db.queryForList("select id from code_chunks where repo_id=? order by id",UUID.class,id));
    }
}
