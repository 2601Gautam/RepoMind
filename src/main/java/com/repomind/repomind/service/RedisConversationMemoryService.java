package com.repomind.repomind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kotlin.uuid.Uuid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisConversationMemoryService implements ConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    //Converstation history expires after 24 hours of inactivity, to prevent unbounded growth in Redis
//    An active conversation resets the timer on every message
    private static final long CONVERSATION_TTL_HOURS = 24;

    //maximum message to store per conversation, to prevent unbounded growth in Redis
    //we store more than we retrieve-retrieve only the last 8 pairs (16 messages) for context, but store up to 100 messages in Redis
    private static final long  MAX_STORED_MESSAGES = 30;

    //Redis key formate: coversation:{conversationId}:messages
    //namespace prefix prevents key collisions with other data in Redis, and makes it easy to find all keys related to a conversation
    private String redisKey(UUID conversationId) {
        return "conversation:" + conversationId + ":messages";
    }

    @Override
    public void addMessage(UUID conversationId, String role, String content) {
        String key = redisKey(conversationId);
        try{
            // Serialize message to JSON string for Redis storage
            String json = objectMapper.writeValueAsString(new MemoryMessage(role,content));
            // Serialize message to JSON string for Redis storage
            // Redis Lists maintain insertion order — oldest messages are at index 0
            redisTemplate.opsForList().rightPush(key,json);

            // Keep only the most recent MAX_STORED_MESSAGES by
// trimming the list to the last MAX_STORED_MESSAGES elements.

            redisTemplate.opsForList().trim(key,-MAX_STORED_MESSAGES,-1);

            // Reset expiry on every write active converstation stay alive
            redisTemplate.expire(key, CONVERSATION_TTL_HOURS, TimeUnit.HOURS);

        }catch (JsonProcessingException e)
        {
            log.warn("Failed to save message to Redis for conversation {}: {}", conversationId, e.getMessage());
            // Non-critical — log and continue, chat still works without memory
        }
    }

    @Override
    public List<MemoryMessage> getRecentMessages(UUID conversationId, int maxMessages)
    {
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
