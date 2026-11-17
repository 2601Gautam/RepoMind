package com.repomind.repomind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisConversationMemoryService implements ConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration CONVERSATION_TTL = Duration.ofDays(5);
    private static final int MAX_STORED_MESSAGES = 10;
    private static final DefaultRedisScript<Long> APPEND = new DefaultRedisScript<>("""
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1)
            return redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            """, Long.class);

    private String redisKey(UUID conversationId) {
        return "conversation:" + conversationId + ":messages";
    }

    @Override
    public void addMessage(UUID conversationId, String role, String content) {
        String key = redisKey(conversationId);
        try {
            String json = objectMapper.writeValueAsString(new MemoryMessage(role, content));
            redisTemplate.execute(APPEND, List.of(key), json, Integer.toString(MAX_STORED_MESSAGES),
                    Long.toString(CONVERSATION_TTL.toSeconds()));
        } catch (JsonProcessingException | DataAccessException e) {
            log.warn("Failed to save message to Redis for conversation {}: {}", conversationId, e.getMessage());
            // Non-critical — log and continue, chat still works without memory
        }
    }

    @Override
    public List<MemoryMessage> getRecentMessages(UUID conversationId, int maxMessages)
    {
        if (maxMessages <= 0) return Collections.emptyList();
        String key = redisKey(conversationId);
        try{
            // LRANGE with negative indices: -maxMessages means "from the end"
            // Gets the most recent maxMessages entries in order (oldest first)

            List<String> jsonMessages = redisTemplate.opsForList()
                    .range(key,-maxMessages,-1);

            if(jsonMessages == null || jsonMessages.isEmpty()){
                return Collections.emptyList();
            }

            return jsonMessages.stream()
                    .map(json -> {
                        try{
                            return objectMapper.readValue(json,MemoryMessage.class);
                        }catch (JsonProcessingException e)
                        {
                            log.warn("Failed to deserialize message from Redis: {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(msg -> msg != null)
                    .toList();
        }catch (Exception e)
        {
            // Redis might be temporarily unavailable
            // Return empty list — chat works without history, just loses context
            log.warn("Failed to retrieve conversation history from Redis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void clearConversation(UUID conversationId) {
        redisTemplate.delete(redisKey(conversationId));
        log.debug("Cleared conversation history for: {}", conversationId);
    }
}
