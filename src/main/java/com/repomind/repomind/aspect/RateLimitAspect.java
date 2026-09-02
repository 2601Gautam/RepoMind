package com.repomind.repomind.aspect;


import com.repomind.repomind.annotation.RateLimit;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    @Around("@annotation(com.repomind.repomind.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable{

        // Get the @RateLimit annotation details
        //signature contain return type and method
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        // Get current user from spring security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String identifier;
        if (authentication != null && authentication.getPrincipal() instanceof User currentUser) {
            // Authenticated request — rate-limit per user, same as before.
            identifier = "user:" + currentUser.getId();
        } else {
            // Unauthenticated request on a @RateLimit-annotated endpoint
            // that is intentionally public (e.g. /api/auth/register,
            // /api/auth/login). There is no user ID yet, so fall back to
            // the caller's IP address. Without this fallback, public
            // endpoints were never rate-limited at all — a script could
            // register unlimited accounts, each getting a fresh per-user
            // bucket, which defeats every other per-user limit in the app
            // (ingestion, chat, etc.) and runs up real AWS/LLM cost.
            identifier = "ip:" + resolveClientIp();
        }

        // Endpoint identifier for the bucket key
        // Uses class name + method name: ChatController.chat
        String endpoint = joinPoint.getTarget().getClass().getSimpleName() + "."+signature.getMethod().getName();

        // Check rate limit — throws RateLimitException if exceeded
        // If not exceeded, execution continues to the actual method
        rateLimitService.checkRateLimit(
                identifier,
                endpoint,
                rateLimit.requests(),
                rateLimit.windowSeconds()
        );

        // Rate limit passed — execute the original method
        return joinPoint.proceed();


    }

    /**
     * Best-effort client IP resolution behind a reverse proxy (Render sits
     * in front of this app). Prefers X-Forwarded-For's first hop — the
     * original client — falling back to the raw remote address if the
     * header is absent (e.g. local dev without a proxy in front).
     * <p>
     * This is inherently spoofable by a client that sets its own
     * X-Forwarded-For header directly against the app; it is a practical
     * deterrent against casual scripted abuse, not a hardened defense.
     * For that, rate limiting should move to the edge (e.g. a WAF or the
     * proxy layer) rather than relying on an app-trusted header.
     */
    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "unknown";
            }
            HttpServletRequest request = attrs.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("Could not resolve client IP for rate limiting: {}", e.getMessage());
            return "unknown";
        }
    }
}