package com.repomind.repomind.service;

// RateLimitService: single responsibility — enforce per-user rate limits
// Uses Bucket4j token bucket algorithm:
//   - Each user gets a bucket with N tokens
//   - Each request consumes one token
//   - Tokens refill at a rate of N per window period
//   - When bucket is empty, request is rejected
//   - Buckets are stored in-memory (per instance) with Redis for distributed scenarios
import com.repomind.repomind.exception.RateLimitException;
import io.github.bucket4j.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitService {

    // In-memory bucket storage
    // For prosuction wirh multiple instances, witch to Redis-backed storage
    // For single Render instance(free tier), in-memory is suffucuent
    // ConcurrentHashMap is thread-safe - multiple requests can hit simultaneously

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Check rate limit for a user and endpoint combination
    // Throws RateLimitException if limit exceeded
    // Returns normally if request is allowed
    public void checkRateLimit(UUID userId, String endpoint , int requests, int windowSeconds) {
        // Unique key per user per endpoint
        // User A's chat limit is independent of User B's chat limit
        // User A's chat limit is independent of User A's debug limit

        String bucketKey = userId + ":" + endpoint;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, key ->
                createBucket(requests, windowSeconds)
        );

        // tryConsume(1): attempt to consume one token
        // Returns true if token was available (request allowed)
        // Returns false if bucket is empty (rate limit exceeded)
        if(!bucket.tryConsume(1)){
            // Calculate how long untill a token is available
            long waitNanos = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
            long waitSeconds = Math.max(1,waitNanos/1_000_000_000);

            log.warn("Rate limit exceeded for user {} on endpoint {}", userId, endpoint);
            throw new RateLimitException(waitSeconds);

        }

    }

    private Bucket createBucket(int requests , int windowSeconds){
        // Refill stratedy: greedy refill adds token continuously
        // not all at once at the end of the window
        // This prevents burst traffic — tokens accumulate smoothly

        Refill refill = Refill.greedy(requests, Duration.ofSeconds(windowSeconds));
        Bandwidth limit = Bandwidth.classic(requests,refill);
        return Bucket.builder().addLimit(limit).build();
    }

}
