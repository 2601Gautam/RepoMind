package com.repomind.repomind.aspect;


import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// AOP Aspect: intercepts all methods annotated with @RateLimit
// Follows Decorator pattern — adds rate limiting behavior without
// modifying the decorated method
// Follows Open/Closed — controllers are closed for modification,
// open for extension via this aspect
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {
    private final RateLimitService rateLimitService;

    // @Around: intercepts the method call entirely
    // ProceedingJoinPoint: let us call the original method (proceed())
    // or skip it (throw exception without calling proceed())

    @Around("@annotation(com.repomind.repomind.annotation.RateLimit")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable{

        // Get the @RateLimit annotation details
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        // Get current user from spring security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !(authentication.getPrincipal() instanceof User)){
            // Not authenticated - Spring Security will return  anyway
            // Let the request proceed to get the proper  response
            return joinPoint.proceed();
        }

        User currentUser = (User) authentication.getPrincipal();

        // Endpoint identifier for the bucket key
        // Uses class name + method name: ChatController.chat

        String endpoint = joinPoint.getTarget().getClass().getSimpleName() + "."+signature.getMethod().getName();

        // Check rate limit — throws RateLimitException if exceeded
        // If not exceeded, execution continues to the actual method
        rateLimitService.checkRateLimit(
                currentUser.getId(),
                endpoint,
                rateLimit.requests(),
                rateLimit.windowSeconds()
        );

        // Rate limit passed — execute the original method
        return joinPoint.proceed();


    }
}
