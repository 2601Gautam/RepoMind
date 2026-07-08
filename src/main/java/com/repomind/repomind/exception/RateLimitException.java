package com.repomind.repomind.exception;

import lombok.Getter;

// Custom exception for rate limit violations
// Carries retryAfterSeconds so the frontend can show a countdown
// "You can retry in 23 seconds" is better UX than a generic error
@Getter
public class RateLimitException extends RuntimeException{
    private final long retryAfterSeconds;
    public RateLimitException(long retryAfterSeconds){
        super("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
