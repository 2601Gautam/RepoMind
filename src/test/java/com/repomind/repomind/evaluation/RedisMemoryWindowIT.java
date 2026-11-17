package com.repomind.repomind.evaluation;

import com.repomind.repomind.service.RedisConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("eval")
class RedisMemoryWindowIT {
    @Autowired RedisConversationMemoryService memory;
    @Autowired StringRedisTemplate redis;

    @Test void zeroAndNegativeWindowReturnNothing() {
        UUID id = UUID.randomUUID();
        try {
            memory.addMessage(id, "user", "one");
            assertAll(() -> assertTrue(memory.getRecentMessages(id, 0).isEmpty()),
                    () -> assertTrue(memory.getRecentMessages(id, -2).isEmpty()));
        } finally { memory.clearConversation(id); }
    }

    @Test void boundedWindowIsOrderedIsolatedAndRenewsExpiryOnWrite() {
        UUID id = UUID.randomUUID();
        String key = "conversation:" + id + ":messages";
        try {
            for (int i = 0; i < 12; i++) memory.addMessage(id, "user", "message " + i);
            assertEquals(10L, redis.opsForList().size(key));
            assertEquals("message 2", memory.getRecentMessages(id, 100).getFirst().content());
            assertTrue(memory.getRecentMessages(UUID.randomUUID(), 4).isEmpty());
            redis.expire(key, 2, TimeUnit.MINUTES);
            memory.addMessage(id, "assistant", "message 12");
            var recent = memory.getRecentMessages(id, 4);
            assertEquals(List.of("message 9", "message 10", "message 11", "message 12"),
                    recent.stream().map(m -> m.content()).toList());
            assertEquals("assistant", recent.getLast().role());
            long ttl = redis.getExpire(key);
            assertTrue(ttl >= 431990 && ttl <= 432000);
            redis.opsForList().rightPush(key, "{bad json");
            assertEquals("message 12", memory.getRecentMessages(id, 2).getFirst().content());
            memory.clearConversation(id);
            assertTrue(memory.getRecentMessages(id, 4).isEmpty());
        } finally { memory.clearConversation(id); }
    }
}
