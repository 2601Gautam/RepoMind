package com.repomind.repomind.service;

// RateLimitService: single responsibility — enforce rate limits keyed by
// an arbitrary identifier + endpoint.
//
// The identifier is either an authenticated user's ID (for endpoints that
// require auth) or the caller's IP address (for public endpoints like
// register/login, where there is no user ID yet). Keying on a plain String
// rather than UUID lets both cases share one bucket store instead of
// needing two parallel rate-limit paths.
//
// Uses Bucket4j token bucket algorithm:
//   - Each identifier+endpoint pair gets a bucket with N tokens
//   - Each request consumes one token
//   - Tokens refill at a rate of N per window period
//   - When bucket is empty, request is rejected
//   - Buckets are stored in-memory (per instance) — for production with
//     multiple instances, switch to Redis-backed storage so limits are
//     shared across instances instead of reset per instance.
import com.repomind.repomind.exception.RateLimitException;
import io.github.bucket4j.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitService {

    // In-memory bucket storage
    // For production with multiple instances, switch to Redis-backed storage
    // For single Render instance (free tier), in-memory is sufficient
    // ConcurrentHashMap is thread-safe - multiple requests can hit simultaneously

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Check rate limit for an identifier + endpoint combination.
     * Throws RateLimitException if limit exceeded.
     * Returns normally if request is allowed.
     *
     * @param identifier an authenticated user's ID (as a string) for
     *                   authenticated endpoints, or the caller's IP
     *                   address for public/unauthenticated endpoints.
     */
    public void checkRateLimit(String identifier, String endpoint, int requests, int windowSeconds) {
        // Unique key per identifier per endpoint
        // User A's chat limit is independent of User B's chat limit
        // User A's chat limit is independent of User A's debug limit
        // Two different IPs hitting /register are independent of each other

        String bucketKey = identifier + ":" + endpoint;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, key ->
                createBucket(requests, windowSeconds)
        );

        // tryConsume(1): attempt to consume one token
        // Returns true if token was available (request allowed)
        // Returns false if bucket is empty (rate limit exceeded)
        if(!bucket.tryConsume(1)){
            // Calculate how long until a token is available
            long waitNanos = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
            long waitSeconds = Math.max(1,waitNanos/1_000_000_000);

            log.warn("Rate limit exceeded for {} on endpoint {}", identifier, endpoint);
            throw new RateLimitException(waitSeconds);

        }

    }

    private Bucket createBucket(int requests , int windowSeconds){
        // Refill strategy: greedy refill adds tokens continuously
        // not all at once at the end of the window
        // This prevents burst traffic — tokens accumulate smoothly

        Refill refill = Refill.greedy(requests, Duration.ofSeconds(windowSeconds));
        Bandwidth limit = Bandwidth.classic(requests,refill);
        return Bucket.builder().addLimit(limit).build();
    }

}