package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.RepomindApplication;
import com.repomind.repomind.service.ingestion.IngestionService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Deliberately halts only the forked evaluation JVM once all four workers have committed their first chunk. */
public class QueueCrashChild {
    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("queue.eval.crashChild") || args.length != 3)
            throw new IllegalStateException("Only run from QueueCrashIT");
        String transport = args[0];
        List<UUID> ids = Files.readAllLines(Path.of(args[2])).stream().map(UUID::fromString).toList();
        try (var ctx = new SpringApplicationBuilder(RepomindApplication.class, QueueFixture.class).run(
                "--spring.profiles.active=eval", "--server.port=0", "--ingestion.embedding-batch-size=1",
                "--aws.sqs.ingestion-lease-seconds=8");
             var sqs = QueueFixture.client()) {
            var db = ctx.getBean(JdbcTemplate.class);
            if (!"repomind_eval".equals(db.queryForObject("select current_database()",String.class)))
                throw new IllegalStateException("Unexpected database");
            var processing = new CountDownLatch(4);
            ctx.getBean(QueueFixture.Work.class).atSecondFile = () -> {
                processing.countDown();
                try {
                    if (!processing.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("Crash barrier timed out");
                } catch (InterruptedException e) { throw new IllegalStateException(e); }
                Runtime.getRuntime().halt(73);
            };
            var service = ctx.getBean(IngestionService.class);
            if (transport.equals("sqs")) {
                var mapper = ctx.getBean(ObjectMapper.class);
                var publisher = QueueFixture.publisher(sqs,mapper,args[1]);
                for (UUID id : ids) publisher.enqueue(id,QueueFixture.url(id),null);
                var worker = QueueFixture.consumer(sqs,mapper,args[1],service,4,2,0);
                while (processing.getCount() > 0) { worker.pollQueue(); Thread.sleep(50); }
                worker.shutdown();
            } else if (transport.equals("direct")) {
                try (var pool = Executors.newFixedThreadPool(4)) {
                    var allAccepted = new CountDownLatch(1);
                    for (UUID id : ids) pool.submit(() -> {
                        allAccepted.await();
                        service.processIngestionJob(id,QueueFixture.url(id),null);
                        return null;
                    });
                    allAccepted.countDown();
                }
            } else throw new IllegalArgumentException(transport);
            throw new IllegalStateException("Child did not reach its crash boundary");
        }
    }
}
