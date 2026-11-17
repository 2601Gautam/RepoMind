package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ingestion.EmbeddingRetryPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in benchmark of three embedding-failure behaviours against a simulated rate-limited provider.
 * <ul>
 * <li><b>original</b>: the first checked-in logic. Retries NonTransientAiException (every 4xx, so 429 but also a bad key)
 *     with 1, 2, 4, 8 s waits, falls back to one call per chunk on any failure and silently skips chunks that still fail.</li>
 * <li><b>current</b>: the logic before this change. Retries only TransientAiException (5xx), so a 429 ends the attempt;
 *     exhausted 5xx falls back to one call per chunk; the job is then released and restarts from scratch.</li>
 * <li><b>new</b>: EmbeddingRetryPolicy, shared by all workers.</li>
 * </ul>
 * Time is scaled by 1/50 (a one second wait lasts 20 ms) so each run takes seconds. Wall time is reported back in
 * equivalent seconds. This models application-level calls; the HTTP stack beneath adds up to two requests per call
 * (see EmbeddingProviderIT), which is the same for all three. Provider limits, latencies and job size are assumptions,
 * not measurements of Mistral. A job that fails is "released": its partial index is discarded and it restarts, up to
 * MAX_RECEIVES times (the queue's redrive limit), exactly as the production claim logic does.
 */
class EmbeddingRateLimitBenchIT {
    static final int SCALE = 50, BATCHES = 10, CHUNKS = 10, MAX_RECEIVES = 3, REPEATS = 3;
    static final long ACCEPT_MS = 4;   // 200 ms of real provider work per accepted call, scaled

    static NonTransientAiException e429() { return new NonTransientAiException("HTTP 429 - {\"message\":\"Rate limit exceeded\"}"); }
    static NonTransientAiException e401() { return new NonTransientAiException("HTTP 401 - {\"detail\":\"Invalid API Key\"}"); }
    static TransientAiException e503() { return new TransientAiException("HTTP 503 - {\"message\":\"Service unavailable\"}"); }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }

    /** Token bucket with an optional initial outage and an optional permanently bad key. */
    static final class Provider {
        final double ratePerSec; final int burst; final long outageUntilNanos; final boolean badKey;
        double tokens; long last = System.nanoTime();
        final AtomicInteger calls = new AtomicInteger(), accepted = new AtomicInteger(), rejected = new AtomicInteger();
        Provider(double ratePerSec, int burst, long outageMs, boolean badKey) {
            this.ratePerSec = ratePerSec; this.burst = burst; this.tokens = burst; this.badKey = badKey;
            this.outageUntilNanos = System.nanoTime() + outageMs * 1_000_000L;
        }
        void call() {
            calls.incrementAndGet();
            if (badKey) { rejected.incrementAndGet(); throw e401(); }
            if (System.nanoTime() < outageUntilNanos) { rejected.incrementAndGet(); throw e503(); }
            synchronized (this) {
                long now = System.nanoTime();
                tokens = Math.min(burst, tokens + (now - last) / 1e9 * ratePerSec); last = now;
                if (tokens < 1) { rejected.incrementAndGet(); throw e429(); }
                tokens -= 1;
            }
            sleep(ACCEPT_MS);
            accepted.incrementAndGet();
        }
    }

    static final class JobFailed extends RuntimeException { JobFailed(Throwable cause) { super(cause); } }

    interface Arm { int embedBatch(Provider p); }   // returns chunks lost; throws JobFailed to release the job

    static <T> T legacyRetry(Predicate<RuntimeException> retryable, Supplier<T> call) {
        long delay = 1000 / SCALE;
        for (int i = 0; ; i++) {
            try { return call.get(); }
            catch (RuntimeException e) {
                if (!retryable.test(e) || i == 4) throw e;
                sleep(delay); delay *= 2;
            }
        }
    }

    static Arm original() {
        return p -> {
            try { legacyRetry(e -> e instanceof NonTransientAiException, () -> { p.call(); return 1; }); return 0; }
            catch (RuntimeException batchFailure) {
                int lost = 0;
                for (int i = 0; i < CHUNKS; i++) {
                    try { legacyRetry(e -> e instanceof NonTransientAiException, () -> { p.call(); return 1; }); }
                    catch (RuntimeException skipped) { lost++; }
                }
                return lost;
            }
        };
    }

    static Arm current() {
        return p -> {
            try { legacyRetry(e -> e instanceof TransientAiException, () -> { p.call(); return 1; }); return 0; }
            catch (NonTransientAiException e) { throw new JobFailed(e); }
            catch (RuntimeException exhausted) {
                try { for (int i = 0; i < CHUNKS; i++) legacyRetry(e -> e instanceof TransientAiException, () -> { p.call(); return 1; }); return 0; }
                catch (RuntimeException e) { throw new JobFailed(e); }
            }
        };
    }

    static Arm fresh(EmbeddingRetryPolicy shared) {
        return p -> {
            try { shared.execute(() -> { p.call(); return 1; }); return 0; }
            catch (RuntimeException e) { throw new JobFailed(e); }
        };
    }

    record JobResult(boolean completed, int receives, int wastedBatches, int lostChunks) {}

    static JobResult runJob(Provider p, Arm arm) {
        int wasted = 0;
        for (int receive = 1; receive <= MAX_RECEIVES; receive++) {
            int done = 0, lost = 0;
            try {
                for (int b = 0; b < BATCHES; b++) { lost += arm.embedBatch(p); done++; }
                return new JobResult(true, receive, wasted, lost);
            } catch (JobFailed released) { wasted += done; }   // partial index is cleared when the job is claimed again
        }
        return new JobResult(false, MAX_RECEIVES, wasted, 0);
    }

    record Scenario(String name, String description, int workers, double ratePerSec, int burst, long outageMs, boolean badKey) {}

    Map<String, Object> run(Scenario s, String armName) throws Exception {
        List<Map<String, Object>> runs = new ArrayList<>();
        for (int r = 0; r < REPEATS; r++) {
            var provider = new Provider(s.ratePerSec(), s.burst(), s.outageMs(), s.badKey());
            Arm arm = switch (armName) {
                case "original" -> original();
                case "current" -> current();
                default -> fresh(new EmbeddingRetryPolicy(8, 1000 / SCALE, 30_000 / SCALE, 180_000 / SCALE, 3, 8_000 / SCALE,
                        EmbeddingRateLimitBenchIT::sleep, System::nanoTime, () -> ThreadLocalRandom.current().nextDouble()));
            };
            var pool = Executors.newFixedThreadPool(s.workers());
            long start = System.nanoTime();
            List<Future<JobResult>> futures = new ArrayList<>();
            for (int w = 0; w < s.workers(); w++) futures.add(pool.submit(() -> runJob(provider, arm)));
            List<JobResult> results = new ArrayList<>();
            for (var f : futures) results.add(f.get(10, TimeUnit.MINUTES));
            pool.shutdown();
            double equivalentSeconds = (System.nanoTime() - start) / 1e9 * SCALE;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobsCompleted", results.stream().filter(JobResult::completed).count());
            row.put("jobsGivenUp", results.stream().filter(x -> !x.completed()).count());
            row.put("receivesUsed", results.stream().mapToInt(JobResult::receives).sum());
            row.put("chunksSilentlyLost", results.stream().mapToInt(JobResult::lostChunks).sum());
            row.put("batchesThrownAway", results.stream().mapToInt(JobResult::wastedBatches).sum());
            row.put("providerCalls", provider.calls.get());
            row.put("rejectedCalls", provider.rejected.get());
            row.put("acceptedCalls", provider.accepted.get());
            row.put("equivalentSeconds", Math.round(equivalentSeconds * 10) / 10.0);
            runs.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("arm", armName);
        for (String key : runs.getFirst().keySet()) {
            double[] values = runs.stream().mapToDouble(x -> ((Number) x.get(key)).doubleValue()).sorted().toArray();
            summary.put(key, Map.of("median", values[values.length / 2], "min", values[0], "max", values[values.length - 1]));
        }
        summary.put("runs", runs);
        return summary;
    }

    @Test void compareFailureBehaviours() throws Exception {
        List<Scenario> scenarios = List.of(
                new Scenario("rate-limit-4-workers", "4 jobs share a provider that allows about 1.2 requests per second (60 per scaled second, burst 2)", 4, 60, 2, 0, false),
                new Scenario("rate-limit-1-worker", "one job against the same limit", 1, 60, 2, 0, false),
                new Scenario("outage-then-limit", "4 jobs, provider answers 503 for 15 s then applies the same rate limit", 4, 60, 2, 300, false),
                new Scenario("bad-api-key", "4 jobs, every call is rejected with 401", 4, 1000, 1000, 0, true));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scale", "1/" + SCALE + " (one scaled second of waiting lasts " + 1000 / SCALE + " ms); wall time reported in equivalent seconds");
        report.put("job", BATCHES + " batches of " + CHUNKS + " chunks, released and restarted up to " + MAX_RECEIVES + " times");
        report.put("repeats", REPEATS);
        List<Object> out = new ArrayList<>();
        for (Scenario s : scenarios) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("scenario", s.name()); entry.put("description", s.description()); entry.put("workers", s.workers());
            List<Object> arms = new ArrayList<>();
            for (String arm : List.of("original", "current", "new")) {
                var summary = run(s, arm);
                arms.add(summary);
                System.out.println("BENCH " + s.name() + " " + arm + " " + summary.entrySet().stream().filter(e -> !e.getKey().equals("runs") && !e.getKey().equals("arm"))
                        .map(e -> e.getKey() + "=" + ((Map<?, ?>) e.getValue()).get("median")).toList());
            }
            entry.put("arms", arms); out.add(entry);
        }
        report.put("scenarios", out);
        Files.createDirectories(Path.of("target"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("target/embedding-retry-bench.json").toFile(), report);

        // Guarantees of the new policy, not tuned to the old behaviours.
        for (Object o : out) {
            @SuppressWarnings("unchecked") var entry = (Map<String, Object>) o;
            @SuppressWarnings("unchecked") var arms = (List<Map<String, Object>>) entry.get("arms");
            var fresh = arms.get(2);
            String name = (String) entry.get("scenario");
            int workers = (Integer) entry.get("workers");
            if (name.equals("bad-api-key")) {
                // Fails fast: one call per receive per job, no fan-out and no pointless waiting.
                assertEquals((double) workers * MAX_RECEIVES, ((Map<?, ?>) fresh.get("providerCalls")).get("max"));
                assertEquals(0.0, ((Map<?, ?>) fresh.get("chunksSilentlyLost")).get("max"));
            } else {
                assertEquals((double) workers, ((Map<?, ?>) fresh.get("jobsCompleted")).get("min"), name + ": every job completes in every run");
                assertEquals(0.0, ((Map<?, ?>) fresh.get("chunksSilentlyLost")).get("max"), name + ": nothing is silently skipped");
                assertEquals(0.0, ((Map<?, ?>) fresh.get("batchesThrownAway")).get("max"), name + ": no completed work is discarded");
            }
        }
    }
}
