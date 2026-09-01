package com.repomind.repomind.annotation;

import java.lang.annotation.*;

// Custom annotation for declarative rate limiting
// Follows Open/Closed principle — adding rate limiting to new endpoints
// requires only adding this annotation, not modifying RateLimitAspect
//
// Usage:
// @RateLimit(requests = 10, windowSeconds = 60)
// @PostMapping("/chat")
// public ... chat(...) { }

@Target(ElementType.METHOD)
//This restricts where @RateLimit can be used — only on methods.
// If someone tried to put @RateLimit on a class or a field, it would fail to compile.
// This makes sense here since you're rate-limiting specific endpoints (methods), not entire classes.
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    // Maximum number of requests allowed in the time window
    int requests();

    // Time window in seconds
    // Default 60 = requests per minute
    int windowSeconds() default 60;
}
