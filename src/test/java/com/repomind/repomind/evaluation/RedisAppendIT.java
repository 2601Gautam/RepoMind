package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ConversationMemoryService.MemoryMessage;
import com.repomind.repomind.service.RedisConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("eval")
class RedisAppendIT {
    @Autowired StringRedisTemplate redis;
    @Autowired RedisConversationMemoryService memory;
    @Autowired ObjectMapper json;

    record Pair(UUID oldId, UUID newId) {}
    record Sample(int round, int write, String mode, long microseconds) {}
    private static String key(UUID id) { return "conversation:" + id + ":messages"; }

    // The three client calls used by the original service at 7988a6d7.
    private void oldAppend(UUID id, MemoryMessage message) throws Exception {
        String key = key(id);
        redis.opsForList().rightPush(key, json.writeValueAsString(message));
        redis.opsForList().trim(key, -10, -1);
        redis.expire(key, Duration.ofDays(5));
    }

    private Properties commandStats() {
        return redis.execute((RedisCallback<Properties>) c -> c.serverCommands().info("commandstats"));
    }

    private long calls(Properties stats, String command) {
        String value = stats.getProperty("cmdstat_" + command, "calls=0,");
        return Long.parseLong(value.substring("calls=".length(), value.indexOf(',')));
    }

    @Test void pairedAppendPreservesStateAndUsesOneWarmScriptCall() throws Exception {
        var pairs = new ArrayList<Pair>();
        var samples = new ArrayList<Sample>();
        for (int i = 0; i < 10; i++) pairs.add(new Pair(UUID.randomUUID(), UUID.randomUUID()));
        try {
            for (int i = 0; i < 100; i++) {
                Pair pair = pairs.get(i % pairs.size());
                var message = new MemoryMessage("user", "warmup " + i);
                oldAppend(pair.oldId(), message);
                memory.addMessage(pair.newId(), message.role(), message.content());
            }
            Properties before = commandStats();
            for (int round = 0; round < 3; round++) {
                for (int i = 0; i < 200; i++) {
                    Pair pair = pairs.get(i % pairs.size());
                    var message = new MemoryMessage((i / pairs.size()) % 2 == 0 ? "user" : "assistant",
                            round + "/" + i + " " + "synthetic context ".repeat(16));
                    for (int side = 0; side < 2; side++) {
                        boolean old = (round + i + side) % 2 == 0;
                        long start = System.nanoTime();
                        if (old) oldAppend(pair.oldId(), message);
                        else memory.addMessage(pair.newId(), message.role(), message.content());
                        long micros = (System.nanoTime() - start) / 1000;
                        samples.add(new Sample(round, i, old ? "threeCalls" : "script", micros));
                    }
                }
                for (Pair pair : pairs) {
                    var expected = memory.getRecentMessages(pair.oldId(), 10);
                    assertEquals(10, expected.size());
                    assertEquals(expected, memory.getRecentMessages(pair.newId(), 10));
                    assertEquals(10L, redis.opsForList().size(key(pair.newId())));
                    for (UUID id : List.of(pair.oldId(), pair.newId())) {
                        long ttl = redis.getExpire(key(id));
                        assertTrue(ttl > 431980 && ttl <= 432000);
                    }
                }
            }
            Properties after = commandStats();
            long evalSha = calls(after, "evalsha") - calls(before, "evalsha");
            long eval = calls(after, "eval") - calls(before, "eval");
            assertEquals(600L, evalSha);
            assertEquals(0L, eval);
            json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/redis-append.json").toFile(),
                    Map.of("warmupPerPath",100, "timedWritesPerPath",600, "rounds",3,
                            "serverEvalshaDelta",evalSha, "serverEvalDelta",eval, "samples",samples));
        } finally {
            for (Pair pair : pairs) {
                memory.clearConversation(pair.oldId());
                memory.clearConversation(pair.newId());
            }
        }
    }
}
