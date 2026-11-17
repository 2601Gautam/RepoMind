package com.repomind.repomind.evaluation;

import com.repomind.repomind.controller.IngestionController;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.service.RedisConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.data.redis.url=redis://127.0.0.1:1", "spring.data.redis.connect-timeout=250ms",
        "spring.data.redis.timeout=250ms", "spring.cache.type=redis"
})
@ActiveProfiles("eval")
class RedisAvailabilityIT {
    @Autowired RedisConversationMemoryService memory;
    @Autowired IngestionController controller;
    @Autowired com.repomind.repomind.service.CacheService cacheService;

    @Test void missingMemoryReadReturnsEmptyWhenRedisIsUnavailable() {
        assertTrue(memory.getRecentMessages(UUID.randomUUID(), 4).isEmpty());
    }

    @Test void optionalMemoryWriteLetsChatContinueWhenRedisIsUnavailable() {
        assertDoesNotThrow(() -> memory.addMessage(UUID.randomUUID(), "user", "hello"));
    }

    @Test void repositoryListFallsBackToTheDatabaseWhenRedisIsUnavailable() {
        User user = User.builder().id(UUID.randomUUID()).build();
        assertDoesNotThrow(() -> assertTrue(controller.listAll(user).isEmpty()), "the cache failure is logged and the list is read from PostgreSQL");
    }

    @Test void evictionFailureDoesNotFailTheCaller() {
        assertDoesNotThrow(() -> cacheService.evictUserReposCache());
    }
}
