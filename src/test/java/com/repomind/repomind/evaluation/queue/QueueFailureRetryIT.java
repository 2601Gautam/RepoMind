package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ingestion.IngestionService;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import com.repomind.repomind.service.ingestion.FileCloneService;
import org.junit.jupiter.api.*;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"ingestion.embedding-batch-size=1", "ingestion.max-embed-retries=1"})
@ActiveProfiles("eval") @Import(QueueFixture.class)
class QueueFailureRetryIT {
    @Autowired JdbcTemplate db;
    @Autowired IngestionService service;
    @Autowired ObjectMapper json;
    @Autowired QueueFixture.Work work;
    @Autowired FileCloneService clone;
    @Autowired EmbeddingService embeddings;
    SqsClient sqs;
    String queue;
    UUID id = UUID.randomUUID();
    String function = "retry_fault_" + id.toString().replace("-", "");

    @BeforeEach void setup() {
        assertEquals("repomind_eval", db.queryForObject("select current_database()", String.class));
        sqs = QueueFixture.client();
        queue = sqs.createQueue(b -> b.queueName("retry-" + id)).queueUrl();
        db.update("insert into repositories(id,github_url,status) values(?,?,'PENDING')", id, QueueFixture.url(id));
        clearInvocations(embeddings);
    }

    @AfterEach void clean() {
        work.atSecondFile = () -> {};
        work.beforeClone = () -> {};
        db.execute("drop function if exists " + function + "() cascade");
        if (queue != null) sqs.deleteQueue(b -> b.queueUrl(queue));
        if (sqs != null) sqs.close();
        db.update("delete from repositories where id=?", id);
    }

    void send() { QueueFixture.publisher(sqs,json,queue).enqueue(id,QueueFixture.url(id),null); }
    void processOne() {
        var consumer = QueueFixture.consumer(sqs,json,queue,service,1,1,0);
        try { consumer.pollQueue(); } finally { consumer.shutdown(); }
    }
    String status() { return db.queryForObject("select status from repositories where id=?",String.class,id); }
    int chunks() { return db.queryForObject("select count(*) from code_chunks where repo_id=?",Integer.class,id); }
    void assertRetained() throws Exception {
        Thread.sleep(1200);
        var messages = sqs.receiveMessage(b -> b.queueUrl(queue).visibilityTimeout(0).waitTimeSeconds(3)
                .attributeNamesWithStrings("ApproximateReceiveCount")).messages();
        assertEquals(1,messages.size(),"Failed work must remain available in SQS");
        assertTrue(Integer.parseInt(messages.getFirst().attributesAsStrings().get("ApproximateReceiveCount")) >= 2);
    }
    void assertRecovered() throws Exception {
        processOne();
        assertEquals("READY",status());
        assertEquals(2,chunks());
        assertEquals(List.of("First.java","Second.java"),db.queryForList(
                "select file_path from code_chunks where repo_id=? order by file_path",String.class,id));
        assertNull(db.queryForObject("select error_message from repositories where id=?",String.class,id));
        Thread.sleep(1200);
        assertTrue(sqs.receiveMessage(b -> b.queueUrl(queue)).messages().isEmpty());
    }

    @Test void embeddingOutageRetainsReceiptAndRetriesPartialIndex() throws Exception {
        work.atSecondFile = () -> { throw new TransientAiException("fixture embedding outage"); };
        send(); processOne();
        assertRetained();
        assertEquals("PENDING",status());
        assertEquals(1,chunks(),"The first file committed before the injected outage");
        assertNull(db.queryForObject("select ingestion_owner from repositories where id=?",UUID.class,id));
        work.atSecondFile = () -> {};
        assertRecovered();
    }

    @Test void databaseWriteFailureRetainsReceiptAndRetriesPartialIndex() throws Exception {
        db.execute("create function " + function + "() returns trigger language plpgsql as $$ begin " +
                "if NEW.repo_id = '" + id + "'::uuid and NEW.file_path = 'Second.java' then " +
                "raise exception 'fixture serialization failure' using errcode = '40001'; end if; return NEW; end $$");
        db.execute("create trigger " + function + " before insert on code_chunks for each row execute function " + function + "()");
        send(); processOne();
        assertRetained();
        assertEquals("PENDING",status());
        assertEquals(1,chunks());
        db.execute("drop function " + function + "() cascade");
        assertRecovered();
    }

    @Test void failedRetryStateWriteLeavesMessageAndRecoversAfterLeaseExpiry() throws Exception {
        db.execute("create function " + function + "() returns trigger language plpgsql as $$ begin " +
                "if NEW.id = '" + id + "'::uuid and NEW.status = 'PENDING' then " +
                "raise exception 'fixture release unavailable' using errcode = '08006'; end if; return NEW; end $$");
        db.execute("create trigger " + function + " before update on repositories for each row execute function " + function + "()");
        work.atSecondFile = () -> { throw new TransientAiException("fixture embedding outage"); };
        send(); processOne();
        assertRetained();
        assertEquals("PROCESSING",status());
        assertNotNull(db.queryForObject("select ingestion_owner from repositories where id=?",UUID.class,id));
        assertEquals(1,chunks());
        db.execute("drop function " + function + "() cascade");
        work.atSecondFile = () -> {};
        db.update("update repositories set ingestion_lease_until=localtimestamp-interval '1 second' where id=?",id);
        assertRecovered();
    }

    @Test void persistentEmbeddingFailureReachesDeadLetterQueue() throws Exception {
        String dead = sqs.createQueue(b -> b.queueName("retry-dead-" + id)).queueUrl();
        try {
            String arn = sqs.getQueueAttributes(b -> b.queueUrl(dead).attributeNames(QueueAttributeName.QUEUE_ARN))
                    .attributes().get(QueueAttributeName.QUEUE_ARN);
            String policy = json.writeValueAsString(Map.of("deadLetterTargetArn",arn,"maxReceiveCount","2"));
            sqs.setQueueAttributes(b -> b.queueUrl(queue).attributes(Map.of(QueueAttributeName.REDRIVE_POLICY,policy)));
            work.atSecondFile = () -> { throw new TransientAiException("fixture persistent outage"); };
            send();
            String recovered = null;
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
            while (recovered == null && System.nanoTime() < deadline) {
                processOne();
                var messages = sqs.receiveMessage(b -> b.queueUrl(dead)).messages();
                if (!messages.isEmpty()) recovered = messages.getFirst().body();
                else Thread.sleep(250);
            }
            assertNotNull(recovered,"Repeated provider failures must be retained in the DLQ");
            assertEquals(id.toString(),json.readTree(recovered).get("repoId").asText());
            assertEquals("PENDING",status());
            assertEquals(1,chunks(),"Each retry clears the previous partial attempt");
            verify(embeddings,atLeast(4)).embedBatch(anyList());
        } finally { sqs.deleteQueue(b -> b.queueUrl(dead)); }
    }

    @Test void providerConfigurationErrorSkipsLocalFanOutButRetainsReceipt() throws Exception {
        work.atSecondFile = () -> { throw new NonTransientAiException("fixture invalid provider configuration"); };
        send(); processOne();
        assertRetained();
        assertEquals("PENDING",status());
        verify(embeddings,times(2)).embedBatch(anyList());
        verify(embeddings,never()).embed(anyString());
        work.atSecondFile = () -> {};
        assertRecovered();
    }

    @Test void repositoryWithoutSupportedFilesIsTerminalAndAcknowledged() throws Exception {
        Path fixture = Path.of("fixture-only");
        var original = clone.extractFiles(fixture);
        when(clone.extractFiles(fixture)).thenReturn(List.of());
        try {
            send(); processOne();
            assertEquals("FAILED",status());
            assertTrue(db.queryForObject("select error_message from repositories where id=?",String.class,id)
                    .contains("No processable code files"));
            assertEquals(0,chunks());
            Thread.sleep(1200);
            assertTrue(sqs.receiveMessage(b -> b.queueUrl(queue)).messages().isEmpty());
            verifyNoInteractions(embeddings);
        } finally { when(clone.extractFiles(fixture)).thenReturn(original); }
    }
}
