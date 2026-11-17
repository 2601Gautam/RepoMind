package com.repomind.repomind.evaluation.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="ingestion.embedding-batch-size=1")
@ActiveProfiles("eval") @Import(QueueFixture.class)
class QueueCrashIT {
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired IngestionService service;

    record Snapshot(int pending, int processing, int ready, int failed, int chunks, int distinctChunks) {}
    Snapshot snapshot(List<UUID> ids) {
        var bind = String.join(",", Collections.nCopies(ids.size(), "?"));
        return db.queryForObject("""
                select count(*) filter(where status='PENDING'), count(*) filter(where status='PROCESSING'),
                       count(*) filter(where status='READY'), count(*) filter(where status='FAILED'),
                       coalesce(sum(chunks),0), coalesce(sum(distinct_chunks),0)
                from repositories r left join lateral (
                   select count(*) chunks,count(distinct(file_path,chunk_index)) distinct_chunks from code_chunks
                   where repo_id=r.id) c on true where r.id in (
                """ + bind + ")", (rs,n) -> new Snapshot(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),
                rs.getInt(5),rs.getInt(6)),ids.toArray());
    }

    void crash(String transport, String queue, List<UUID> ids, Path directory) throws Exception {
        Path idsFile = directory.resolve(transport + "-ids.txt");
        Files.write(idsFile,ids.stream().map(UUID::toString).toList());
        String cp = System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        // An argument file avoids Windows' command line size limit for the full test classpath.
        Path options = directory.resolve(transport + "-jvm.args");
        Files.writeString(options, "-Dqueue.eval.crashChild=true\n-Dspring.devtools.restart.enabled=false\n-classpath\n\"" +
                cp.replace("\\","/").replace("\"","\\\"") + "\"\n");
        Path log = directory.resolve(transport + "-child.log");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "@" + options.toAbsolutePath(),QueueCrashChild.class.getName(),transport,queue,idsFile.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(65,TimeUnit.SECONDS), "Child timeout: " + log);
            assertEquals(73,child.exitValue(),"Child did not halt at the expected boundary: " + log);
        } finally { if (child.isAlive()) child.destroyForcibly().waitFor(10,TimeUnit.SECONDS); }
    }

    @Test void restartAfterPartialWrites() throws Exception {
        assertEquals("repomind_eval",db.queryForObject("select current_database()",String.class));
        Path directory = Files.createDirectories(Path.of("target/queue-crash"));
        Map<String,Object> report = new LinkedHashMap<>();
        try (var sqs = QueueFixture.client()) {
            for (String transport : List.of("direct","sqs")) {
                List<UUID> ids = new ArrayList<>();
                String queue = sqs.createQueue(b -> b.queueName("crash-" + UUID.randomUUID())).queueUrl();
                try {
                    for (int i=0; i<12; i++) {
                        UUID id = UUID.randomUUID();
                        db.update("insert into repositories(id,github_url,status) values(?,?,'PENDING')",id,QueueFixture.url(id));
                        ids.add(id);
                    }
                    crash(transport,queue,ids,directory);
                    Snapshot before = snapshot(ids);
                    assertEquals(new Snapshot(8,4,0,0,4,4),before);
                    long start = System.nanoTime();
                    if (transport.equals("sqs")) {
                        var restarted = QueueFixture.consumer(sqs,json,queue,service,4,2,0);
                        try {
                            while (snapshot(ids).ready() < 12 && System.nanoTime()-start < Duration.ofSeconds(13).toNanos()) {
                                restarted.pollQueue();
                                Thread.sleep(100);
                            }
                        } finally { restarted.shutdown(); }
                    } else {
                        // A new, empty executor starts with no mechanism to redispatch database rows.
                        try (var restarted = Executors.newFixedThreadPool(4)) {
                            restarted.submit(() -> {}).get();
                            Thread.sleep(Duration.ofSeconds(13));
                        }
                    }
                    Snapshot after = snapshot(ids);
                    report.put(transport, Map.of("accepted",ids.size(),"afterHalt",before,"afterRestart",after,
                            "observedMs",(System.nanoTime()-start)/1_000_000));
                    json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/queue-crash.json").toFile(),report);
                    if (transport.equals("sqs")) assertEquals(new Snapshot(0,0,12,0,24,24),after);
                    else assertEquals(before,after);
                } finally {
                    sqs.deleteQueue(b -> b.queueUrl(queue));
                    for (UUID id : ids) db.update("delete from repositories where id=?",id);
                }
            }
        }
    }
}
