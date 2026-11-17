package com.repomind.repomind.evaluation;

import com.repomind.repomind.service.ingestion.EmbeddingRetryPolicy;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real Spring AI Mistral embedding client talking to a local fake provider that answers with scripted HTTP statuses.
 * Records, with the actual library stack, how each status is classified and how many HTTP requests one embedding call
 * costs. Three retry layers are involved and the counts below pin all of them:
 * <ol>
 * <li>Apache HttpClient 5 (the request factory Spring picks up) re-sends a 429 or 503 once after at least a second,
 *     honoring Retry-After.</li>
 * <li>Spring AI's RetryTemplate retries 5xx and I/O errors; spring.ai.retry.max-attempts is a count of RETRIES.</li>
 * <li>EmbeddingRetryPolicy, the application's own policy.</li>
 * </ol>
 * Only the Mistral URL is redirected to a loopback server; nothing leaves the machine.
 */
@SpringBootTest(properties = {
        "spring.ai.retry.backoff.initial-interval=10ms", "spring.ai.retry.backoff.max-interval=40ms",
        "ingestion.embed-retry.base-delay-ms=20", "ingestion.embed-retry.max-delay-ms=200",
        "ingestion.embed-retry.budget-ms=60000", "ingestion.max-embed-retries=6"})
@ActiveProfiles("eval")
class EmbeddingProviderIT {
    static final HttpServer server;
    static final ConcurrentLinkedQueue<Integer> script = new ConcurrentLinkedQueue<>();
    static final AtomicInteger requests = new AtomicInteger();
    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                Integer status = script.poll();
                String body; int code;
                if (status == null || status == 200) {
                    code = 200;
                    body = "{\"id\":\"x\",\"object\":\"list\",\"model\":\"mistral-embed\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1,\"completion_tokens\":0},"
                            + "\"data\":[{\"object\":\"embedding\",\"embedding\":[0.25,0.5,0.75,1.0],\"index\":0}]}";
                } else {
                    code = status; body = "{\"message\":\"scripted " + status + "\"}";
                    if (status == 429) exchange.getResponseHeaders().add("Retry-After", "1");
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void provider(DynamicPropertyRegistry registry) {
        // The embedding-specific property wins over spring.ai.mistralai.base-url, so it must be the one redirected.
        registry.add("spring.ai.mistralai.embedding.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        registry.add("spring.ai.mistralai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterAll static void stop() { server.stop(0); }

    @Autowired EmbeddingService embeddings;
    @Autowired EmbeddingRetryPolicy policy;
    @Autowired SpringAiRetryProperties libraryRetry;

    @BeforeEach void reset() { script.clear(); requests.set(0); }
    void repeat(int count, int status) { for (int i = 0; i < count; i++) script.add(status); }
    void script(Integer... statuses) { script.addAll(Arrays.asList(statuses)); }

    @Test void configurationBoundsTheMiddleLayerAndLeavesRateLimitsToTheAppPolicy() {
        assertEquals(3, libraryRetry.getMaxAttempts(), "application.yml sets spring.ai.retry.max-attempts (3 retries, 4 attempts)");
        assertFalse(libraryRetry.isOnClientErrors(), "Spring AI does not retry 4xx, so 429 handling belongs to EmbeddingRetryPolicy");
    }

    @Test void aRateLimitGetsOneHttpLevelRetryHonoringRetryAfterAndThenSurfacesAsAClientError() {
        script(429, 200);
        long start = System.nanoTime();
        assertEquals(4, embeddings.embed("q").length);
        assertEquals(2, requests.get(), "Apache HttpClient re-sent the 429");
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 900, "it waited for Retry-After: 1");
        reset();
        repeat(6, 429);
        var error = assertThrows(NonTransientAiException.class, () -> embeddings.embed("q"));
        assertTrue(error.getMessage().startsWith("HTTP 429"), error.getMessage());
        assertEquals(2, requests.get(), "one attempt plus one HTTP-level retry, then Spring AI classes it NonTransient and stops");
        assertEquals(EmbeddingRetryPolicy.Kind.RATE_LIMITED, EmbeddingRetryPolicy.classify(error));
    }

    @Test void aServerErrorOutageCostsTwoRequestsPerLibraryAttempt() {
        script(503, 200);
        assertEquals(4, embeddings.embed("q").length);
        assertEquals(2, requests.get());
        reset();
        repeat(12, 503);
        assertThrows(TransientAiException.class, () -> embeddings.embed("q"));
        assertEquals(2 * (libraryRetry.getMaxAttempts() + 1), requests.get(),
                "2 HTTP requests x (3 retries + 1) Spring AI attempts: the stacking the app policy must not multiply further");
    }

    @Test void policyRidesOutARateLimitThatTheLibraryAloneWouldSurface() {
        repeat(5, 429); script(200);   // attempts 1 and 2 are each rejected twice, attempt 3 is rejected once then accepted
        float[] vector = policy.execute(() -> embeddings.embed("q"));
        assertEquals(4, vector.length);
        assertEquals(6, requests.get());
    }

    @Test void policyDoesNotRetryABadKeyOrABadRequest() {
        for (int status : new int[]{401, 400, 403}) {
            reset(); script(status, 200);
            assertThrows(NonTransientAiException.class, () -> policy.execute(() -> embeddings.embed("q")), "HTTP " + status);
            assertEquals(1, requests.get(), "HTTP " + status + " must not be retried");
        }
    }

    @Test void policySitsOnTopOfTheLibraryAfterItGivesUpOnAnOutage() {
        repeat(8, 503); script(200);   // the library's 4 attempts x 2 requests fail, the policy's second attempt succeeds
        assertEquals(4, policy.execute(() -> embeddings.embed("q")).length);
        assertEquals(9, requests.get());
    }

    @Test void chatQuestionsRetryBrieflyThenFailInsteadOfHanging() {
        repeat(10, 429);
        long start = System.nanoTime();
        var error = assertThrows(EmbeddingRetryPolicy.RetryExhaustedException.class, () -> embeddings.embedForQuery("q"));
        assertEquals(EmbeddingRetryPolicy.Kind.RATE_LIMITED, error.kind());
        assertEquals(3, error.attempts());
        assertEquals(6, requests.get(), "three attempts, two HTTP requests each");
        assertTrue((System.nanoTime() - start) / 1_000_000 < 15_000, "bounded, not minutes");
    }

    @Test void chatQuestionsSurviveAShortRateLimit() {
        script(429, 429, 200);
        assertEquals(4, embeddings.embedForQuery("q").length);
        assertEquals(3, requests.get());
    }
}
