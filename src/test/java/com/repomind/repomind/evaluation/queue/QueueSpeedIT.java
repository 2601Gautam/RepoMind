package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ingestion.IngestionService;
import com.repomind.repomind.service.queue.IngestionQueueConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="ingestion.embedding-batch-size=1")
@ActiveProfiles("eval") @Import(QueueFixture.class)
class QueueSpeedIT {
    @Autowired IngestionService service;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @org.springframework.beans.factory.annotation.Value("${aws.sqs.poll-delay-ms}") long pollMs;
    @org.springframework.beans.factory.annotation.Value("${aws.sqs.long-poll-seconds}") int longPoll;
    @org.springframework.beans.factory.annotation.Value("${aws.sqs.visibility-timeout-seconds}") int visibility;
    record Arm(boolean sqs, int workers) {}
    record Job(long submitMicros, long waitMicros, long completionMicros) {}
    record Batch(int round,String transport,int workers,int peakActive,long batchMicros,List<Job> jobs) {}

    Batch run(SqsClient sqs, Arm arm, int round, int count) throws Exception {
        var ids = new ArrayList<UUID>();
        String queue = sqs.createQueue(b -> b.queueName("speed-" + UUID.randomUUID())).queueUrl();
        Map<UUID, Long> submits = new ConcurrentHashMap<>(), costs = new ConcurrentHashMap<>(),
                starts = new ConcurrentHashMap<>(), ends = new ConcurrentHashMap<>();
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        var finished = new CountDownLatch(count);
        var startGate = new CountDownLatch(1);
        // Both arms execute the same real service, including DB claim, chunk writes and terminal commit.
        var observed = mock(IngestionService.class);
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            starts.put(id,System.nanoTime());
            peak.accumulateAndGet(active.incrementAndGet(),Math::max);
            try { service.processIngestionJob(id,QueueFixture.url(id),null); }
            finally {
                ends.put(id,System.nanoTime()); active.decrementAndGet(); finished.countDown();
            }
            return null;
        }).when(observed).processIngestionJob(any(),anyString(),isNull());
        var publisher = QueueFixture.publisher(sqs,json,queue);
        IngestionQueueConsumer consumer = arm.sqs()
                ? QueueFixture.consumer(sqs,json,queue,observed,arm.workers(),visibility,longPoll) : null;
        ExecutorService direct = arm.sqs() ? null : Executors.newFixedThreadPool(arm.workers());
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            for (int i=0; i<count; i++) {
                UUID id = UUID.randomUUID();
                db.update("insert into repositories(id,github_url,status) values(?,?,'PENDING')",id,QueueFixture.url(id));
                ids.add(id);
            }
            long begin = System.nanoTime();
            for (UUID id : ids) {
                long start = System.nanoTime();
                submits.put(id,start);
                if (arm.sqs()) publisher.enqueue(id,QueueFixture.url(id),null);
                else direct.submit(() -> {
                    startGate.await(); observed.processIngestionJob(id,QueueFixture.url(id),null); return null;
                });
                costs.put(id,(System.nanoTime()-start)/1000);
            }
            startGate.countDown();
            if (consumer != null) scheduler.scheduleWithFixedDelay(consumer::pollQueue,0,pollMs,TimeUnit.MILLISECONDS);
            assertTrue(finished.await(45,TimeUnit.SECONDS),"Batch did not finish");
            long elapsed = (Collections.max(ends.values()) - begin)/1000;
            assertEquals(arm.workers(),peak.get());
            List<Job> jobs = new ArrayList<>();
            for (UUID id : ids) {
                assertEquals("READY",db.queryForObject("select status from repositories where id=?",String.class,id));
                assertEquals(List.of("First.java","Second.java"),db.queryForList(
                        "select file_path from code_chunks where repo_id=? order by file_path",String.class,id));
                jobs.add(new Job(costs.get(id),(starts.get(id)-submits.get(id))/1000,(ends.get(id)-submits.get(id))/1000));
            }
            return new Batch(round,arm.sqs() ? "sqs" : "direct",arm.workers(),peak.get(),elapsed,jobs);
        } finally {
            startGate.countDown(); scheduler.shutdownNow();
            if (consumer != null) consumer.shutdown();
            if (direct != null) direct.close();
            sqs.deleteQueue(b -> b.queueUrl(queue));
            for (UUID id : ids) db.update("delete from repositories where id=?",id);
        }
    }

    @Test void compareTransportsAtEqualConcurrency() throws Exception {
        assertEquals("repomind_eval",db.queryForObject("select current_database()",String.class));
        var arms = List.of(new Arm(false,1),new Arm(true,1),new Arm(false,4),new Arm(true,4));
        // Rotate the first arm and vary ordering to limit fixed start-order effects.
        int[][] order = {{0,1,2,3},{3,2,1,0},{1,3,0,2}};
        var results = new ArrayList<Batch>();
        try (var sqs = QueueFixture.client()) {
            for (Arm arm : arms) run(sqs,arm,-1,4);
            for (int round=0; round<order.length; round++) {
                for (int index : order[round]) {
                    results.add(run(sqs,arms.get(index),round,12));
                    json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/queue-speed.json").toFile(),
                            Map.of("cloneDelayMs",QueueFixture.DELAY_MS,"filesPerJob",2,"warmupJobsPerArm",4,
                                    "pollDelayMs",pollMs,"longPollSeconds",longPoll,"visibilitySeconds",visibility,
                                    "batches",results));
                }
            }
        }
    }
}
