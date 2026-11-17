package com.repomind.repomind.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One retry policy for every embedding call (full ingestion, incremental sync, chat and debug queries).
 *
 * <p>Why it exists: Spring AI 2.0.0 maps every HTTP 4xx, including 429, to {@link NonTransientAiException} and only
 * 5xx to {@link TransientAiException}, and its built-in retry skips client errors by default. The previous code retried
 * the wrong class in one place (every 4xx, even a bad API key) and the right class in another (5xx only, so a rate
 * limit got no local backoff and the whole job restarted). The HTTP status survives only as the message prefix
 * ("HTTP 429 - ..." from the Mistral client, "429 - ..." from Spring AI's default handler), so classification reads it
 * from there; tests cover both formats and drive the real client against a fake provider.
 *
 * <p>This is the outermost of three stacked layers. Underneath, Spring AI's RetryTemplate retries 5xx and network errors
 * (spring.ai.retry.max-attempts is a count of retries, so 3 means 4 attempts) and Apache HttpClient 5 silently re-sends
 * a 429 or 503 once after at least a second, honoring Retry-After. One embedding call can therefore make several HTTP
 * requests, so application.yml keeps the middle layer short.
 *
 * <p>Behavior: rate limits and transient failures are retried with jittered exponential backoff inside an attempt and
 * time budget. After a 429 a pacer delays every thread that shares this bean, so four workers do not keep hammering a
 * limited provider. Anything else (bad key, bad request, unknown) fails immediately. Exhaustion throws
 * {@link RetryExhaustedException}, which callers must not answer with more calls.
 */
@Component
@Slf4j
public class EmbeddingRetryPolicy {
    public enum Kind { RATE_LIMITED, TRANSIENT, FATAL }

    public static class RetryExhaustedException extends RuntimeException {
        private final Kind kind;
        private final int attempts;
        public RetryExhaustedException(Kind kind, int attempts, RuntimeException cause) {
            super("Embedding provider still " + (kind == Kind.RATE_LIMITED ? "rate limiting" : "failing")
                    + " after " + attempts + " attempts: " + cause.getMessage(), cause);
            this.kind = kind; this.attempts = attempts;
        }
        public Kind kind() { return kind; }
        public int attempts() { return attempts; }
    }

    public interface Sleeper { void sleep(long millis); }

    private static final Pattern STATUS = Pattern.compile("^\\s*(?:HTTP\\s+)?(\\d{3})\\b");

    private final int maxAttempts;
    private final long baseDelayMs, maxDelayMs, budgetMs;
    private final int interactiveAttempts;
    private final long interactiveBudgetMs;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;
    private final DoubleSupplier random;
    // Shared across threads: current penalty and the moment the next call may start.
    private final AtomicLong penaltyMs = new AtomicLong();
    private final AtomicLong resumeAtNanos = new AtomicLong();
    private final AtomicLong rateLimited = new AtomicLong(), retried = new AtomicLong(), exhausted = new AtomicLong();

    @Autowired
    public EmbeddingRetryPolicy(
            @Value("${ingestion.max-embed-retries:8}") int maxAttempts,
            @Value("${ingestion.embed-retry.base-delay-ms:1000}") long baseDelayMs,
            @Value("${ingestion.embed-retry.max-delay-ms:30000}") long maxDelayMs,
            @Value("${ingestion.embed-retry.budget-ms:180000}") long budgetMs,
            @Value("${ingestion.embed-retry.interactive-attempts:3}") int interactiveAttempts,
            @Value("${ingestion.embed-retry.interactive-budget-ms:8000}") long interactiveBudgetMs) {
        this(maxAttempts, baseDelayMs, maxDelayMs, budgetMs, interactiveAttempts, interactiveBudgetMs,
                EmbeddingRetryPolicy::parkSleep, System::nanoTime, () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());
    }

    /** Test and benchmark constructor: injectable clock, sleeper and randomness. */
    public EmbeddingRetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs, long budgetMs, int interactiveAttempts,
                                long interactiveBudgetMs, Sleeper sleeper, LongSupplier nanoClock, DoubleSupplier random) {
        if (maxAttempts < 1 || interactiveAttempts < 1) throw new IllegalArgumentException("attempts must be positive");
        this.maxAttempts = maxAttempts; this.baseDelayMs = baseDelayMs; this.maxDelayMs = maxDelayMs; this.budgetMs = budgetMs;
        this.interactiveAttempts = interactiveAttempts; this.interactiveBudgetMs = interactiveBudgetMs;
        this.sleeper = sleeper; this.nanoClock = nanoClock; this.random = random;
    }

    /** Background work (ingestion, sync): patient, bounded by attempts and a total time budget. */
    public <T> T execute(Supplier<T> call) { return run(call, maxAttempts, budgetMs); }

    /** A person is waiting (chat, debug): a few quick attempts, then fail so the UI can say so. */
    public <T> T executeInteractive(Supplier<T> call) { return run(call, interactiveAttempts, interactiveBudgetMs); }

    public static Kind classify(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 8; t = t.getCause(), depth++) {
            if (t instanceof TransientAiException) return Kind.TRANSIENT;
            if (t instanceof NonTransientAiException) {
                Matcher m = STATUS.matcher(String.valueOf(t.getMessage()));
                if (m.find()) {
                    int status = Integer.parseInt(m.group(1));
                    if (status == 429) return Kind.RATE_LIMITED;
                    if (status == 408 || status == 425) return Kind.TRANSIENT;
                }
                return Kind.FATAL;
            }
            if (t instanceof IOException) return Kind.TRANSIENT;   // timeouts and resets, usually wrapped by the HTTP client
        }
        return Kind.FATAL;
    }

    public long rateLimitedCount() { return rateLimited.get(); }
    public long retriedCount() { return retried.get(); }
    public long exhaustedCount() { return exhausted.get(); }

    private <T> T run(Supplier<T> call, int attempts, long budget) {
        long started = nanoClock.getAsLong();
        for (int attempt = 1; ; attempt++) {
            awaitPacer();
            try {
                T result = call.get();
                ease();
                return result;
            } catch (RuntimeException e) {
                Kind kind = classify(e);
                if (kind == Kind.FATAL) throw e;
                long wait;
                if (kind == Kind.RATE_LIMITED) { rateLimited.incrementAndGet(); wait = penalize(); }
                else wait = jitter(attempt);
                long spent = TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - started);
                if (attempt >= attempts || spent + wait > budget) {
                    exhausted.incrementAndGet();
                    throw new RetryExhaustedException(kind, attempt, e);
                }
                retried.incrementAndGet();
                log.warn("Embedding {} (attempt {}/{}); next try in about {} ms", kind == Kind.RATE_LIMITED ? "rate limited" : "failed transiently",
                        attempt, attempts, wait);
                if (kind == Kind.TRANSIENT) sleeper.sleep(wait);   // rate limits wait in awaitPacer() so all threads share it
            }
        }
    }

    /** Equal jitter: half fixed, half random, so concurrent workers do not retry in lockstep. */
    private long jitter(int attempt) {
        long ceiling = Math.min(maxDelayMs, baseDelayMs * (1L << Math.min(attempt - 1, 20)));
        return ceiling / 2 + (long) (random.getAsDouble() * (ceiling / 2.0));
    }

    private long penalize() {
        long next = penaltyMs.updateAndGet(p -> p == 0 ? baseDelayMs : Math.min(maxDelayMs, p * 2));
        long wait = next / 2 + (long) (random.getAsDouble() * (next / 2.0));
        long until = nanoClock.getAsLong() + TimeUnit.MILLISECONDS.toNanos(wait);
        resumeAtNanos.accumulateAndGet(until, Math::max);
        return wait;
    }

    private void ease() { penaltyMs.updateAndGet(p -> p <= baseDelayMs ? 0 : p / 2); }

    private void awaitPacer() {
        long waitNanos = resumeAtNanos.get() - nanoClock.getAsLong();
        if (waitNanos > 0) sleeper.sleep(TimeUnit.NANOSECONDS.toMillis(waitNanos) + 1);
    }

    private static void parkSleep(long millis) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        for (long left = end - System.nanoTime(); left > 0; left = end - System.nanoTime()) {
            LockSupport.parkNanos(left);   // parkNanos may return early, so keep waiting until the deadline
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Retry backoff interrupted");
        }
    }
}
