package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.service.ingestion.EmbeddingRetryPolicy.Kind;
import com.repomind.repomind.service.ingestion.EmbeddingRetryPolicy.RetryExhaustedException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingRetryPolicyTest {
    /** The exception Spring AI 2.0.0 itself throws for this status, so classification is tested against reality. */
    static RuntimeException fromProvider(int status, String body) {
        try {
            RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER.handleError(java.net.URI.create("http://provider.invalid/v1/embeddings"),
                    org.springframework.http.HttpMethod.POST, new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatusCode.valueOf(status)));
        } catch (RuntimeException e) { return e; } catch (IOException e) { throw new IllegalStateException(e); }
        throw new IllegalStateException("status " + status + " did not produce an error");
    }

    /** Virtual clock: sleeping advances time, so waits are measured without waiting. */
    static final class Clock {
        long nanos; final List<Long> sleeps = new ArrayList<>();
        void sleep(long ms) { sleeps.add(ms); nanos += ms * 1_000_000L; }
        long now() { return nanos; }
    }

    static EmbeddingRetryPolicy policy(Clock c, int attempts, long base, long max, long budget) {
        return new EmbeddingRetryPolicy(attempts, base, max, budget, 3, 8000, c::sleep, c::now, () -> 0.5);
    }

    @Test void statusCodesMapToTheRightKindUsingSpringAisOwnMessages() {
        var expected = new LinkedHashMap<Integer, Kind>();
        expected.put(429, Kind.RATE_LIMITED);
        for (int s : new int[]{408, 425, 500, 502, 503, 504}) expected.put(s, Kind.TRANSIENT);
        for (int s : new int[]{400, 401, 403, 404, 413, 422}) expected.put(s, Kind.FATAL);
        expected.forEach((status, kind) -> assertEquals(kind, EmbeddingRetryPolicy.classify(fromProvider(status, "{\"message\":\"x\"}")), "HTTP " + status));
    }

    @Test void theMistralClientsMessageFormatWithAnHttpPrefixIsClassifiedToo() {
        // Observed from the real client against a fake provider: "HTTP 429 - {...}", not "429 - {...}".
        assertEquals(Kind.RATE_LIMITED, EmbeddingRetryPolicy.classify(new NonTransientAiException("HTTP 429 - {\"message\":\"rate limit\"}")));
        assertEquals(Kind.FATAL, EmbeddingRetryPolicy.classify(new NonTransientAiException("HTTP 401 - {\"detail\":\"Invalid API Key\"}")));
        assertEquals(Kind.FATAL, EmbeddingRetryPolicy.classify(new NonTransientAiException("HTTP 400 - bad input")));
        assertEquals(Kind.TRANSIENT, EmbeddingRetryPolicy.classify(new NonTransientAiException("HTTP 408 - timeout")));
        assertEquals(Kind.FATAL, EmbeddingRetryPolicy.classify(new NonTransientAiException("rate limit mentioned 429 in the middle")));
    }

    @Test void springAiClassOfEachStatusIsPinnedSoALibraryUpgradeCannotSilentlyChangeRetryBehaviour() {
        assertInstanceOf(NonTransientAiException.class, fromProvider(429, "slow down"), "429 is a client error in Spring AI 2.0.0");
        assertInstanceOf(TransientAiException.class, fromProvider(503, "busy"));
        assertTrue(fromProvider(429, "slow down").getMessage().startsWith("429 - "));
    }

    @Test void networkFailuresAreTransientAndUnknownErrorsAreNot() {
        assertEquals(Kind.TRANSIENT, EmbeddingRetryPolicy.classify(new ResourceAccessException("I/O error", new SocketTimeoutException("read timed out"))));
        assertEquals(Kind.FATAL, EmbeddingRetryPolicy.classify(new IllegalStateException("count mismatch")));
        assertEquals(Kind.FATAL, EmbeddingRetryPolicy.classify(new NonTransientAiException("fixture invalid provider configuration")));
    }

    @Test void rateLimitIsRetriedUntilTheProviderRecovers() {
        var clock = new Clock(); var calls = new AtomicInteger();
        var result = policy(clock, 8, 100, 1600, 60_000).execute(() -> {
            if (calls.incrementAndGet() <= 3) throw fromProvider(429, "rate limit");
            return "vector";
        });
        assertEquals("vector", result);
        assertEquals(4, calls.get());
        assertEquals(3, clock.sleeps.size());
        assertTrue(clock.sleeps.get(0) < clock.sleeps.get(2), "waits grow: " + clock.sleeps);
    }

    @Test void badKeysAndBadRequestsAreNotRetried() {
        for (int status : new int[]{400, 401, 403}) {
            var clock = new Clock(); var calls = new AtomicInteger();
            var error = assertThrows(NonTransientAiException.class, () -> policy(clock, 8, 100, 1600, 60_000)
                    .execute(() -> { calls.incrementAndGet(); throw fromProvider(status, "no"); }));
            assertEquals(1, calls.get(), "HTTP " + status);
            assertTrue(clock.sleeps.isEmpty());
            assertNotNull(error);
        }
    }

    @Test void exhaustionIsReportedWithKindAndAttemptsAndKeepsTheCause() {
        var clock = new Clock(); var calls = new AtomicInteger();
        var error = assertThrows(RetryExhaustedException.class, () -> policy(clock, 4, 100, 1600, 60_000)
                .execute(() -> { calls.incrementAndGet(); throw fromProvider(429, "rate limit"); }));
        assertEquals(4, calls.get());
        assertEquals(Kind.RATE_LIMITED, error.kind());
        assertEquals(4, error.attempts());
        assertInstanceOf(NonTransientAiException.class, error.getCause());
    }

    @Test void theTimeBudgetStopsRetriesBeforeAttemptsRunOut() {
        var clock = new Clock(); var calls = new AtomicInteger();
        assertThrows(RetryExhaustedException.class, () -> policy(clock, 50, 1000, 30_000, 5_000)
                .execute(() -> { calls.incrementAndGet(); throw fromProvider(503, "down"); }));
        long waited = clock.nanos / 1_000_000L;
        assertTrue(waited <= 5_000, "waited " + waited + " ms");
        assertTrue(calls.get() < 50);
    }

    @Test void backoffIsCappedAndJittered() {
        var clock = new Clock();
        assertThrows(RetryExhaustedException.class, () -> policy(clock, 12, 1000, 8000, 10_000_000)
                .execute(() -> { throw fromProvider(503, "down"); }));
        assertTrue(clock.sleeps.stream().allMatch(ms -> ms >= 500 && ms <= 8000), clock.sleeps.toString());
        var low = new Clock(); var high = new Clock();
        for (var pair : List.of(Map.entry(low, 0.0), Map.entry(high, 0.999))) {
            var p = new EmbeddingRetryPolicy(4, 1000, 8000, 10_000_000, 3, 8000, pair.getKey()::sleep, pair.getKey()::now, pair::getValue);
            assertThrows(RetryExhaustedException.class, () -> p.execute(() -> { throw fromProvider(503, "down"); }));
        }
        assertTrue(low.sleeps.get(0) < high.sleeps.get(0), "random half of the delay differs between callers");
    }

    @Test void afterARateLimitEveryCallerSharingThePolicyWaitsBeforeItsFirstCall() {
        var clock = new Clock(); var p = policy(clock, 8, 1000, 30_000, 600_000);
        var first = new AtomicInteger();
        p.execute(() -> { if (first.incrementAndGet() == 1) throw fromProvider(429, "slow down"); return 1; });
        long afterFirst = clock.sleeps.size();
        clock.sleeps.clear();
        // A different caller (another worker) starts now: the pacer penalty from the 429 is still in force or has just elapsed.
        p.execute(() -> 2);
        assertTrue(afterFirst >= 1);
        assertTrue(p.rateLimitedCount() == 1);
    }

    @Test void aPenaltyEasesAfterSuccessSoANormalProviderIsNotSlowedForever() {
        var clock = new Clock(); var p = policy(clock, 8, 1000, 30_000, 600_000);
        var n = new AtomicInteger();
        p.execute(() -> { if (n.incrementAndGet() <= 2) throw fromProvider(429, "slow"); return 1; });
        for (int i = 0; i < 6; i++) p.execute(() -> 1);
        clock.sleeps.clear();
        p.execute(() -> 1);
        assertTrue(clock.sleeps.isEmpty(), "no waiting once the provider has been healthy for a while: " + clock.sleeps);
    }

    @Test void interactiveCallsGiveUpQuicklySoThePersonWaitingGetsAnAnswer() {
        var clock = new Clock(); var calls = new AtomicInteger();
        assertThrows(RetryExhaustedException.class, () -> policy(clock, 8, 1000, 30_000, 180_000)
                .executeInteractive(() -> { calls.incrementAndGet(); throw fromProvider(429, "slow"); }));
        assertEquals(3, calls.get());
        assertTrue(clock.nanos / 1_000_000L <= 8_000, "waited " + clock.nanos / 1_000_000L + " ms");
    }

    @Test void aSingleAttemptConfigurationBehavesLikeNoRetry() {
        var clock = new Clock(); var calls = new AtomicInteger();
        assertThrows(RetryExhaustedException.class, () -> policy(clock, 1, 100, 100, 1000)
                .execute(() -> { calls.incrementAndGet(); throw fromProvider(503, "down"); }));
        assertEquals(1, calls.get());
        assertTrue(clock.sleeps.isEmpty());
    }
}
