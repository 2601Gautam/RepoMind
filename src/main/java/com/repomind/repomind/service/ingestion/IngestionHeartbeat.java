package com.repomind.repomind.service.ingestion;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor @Slf4j
public class IngestionHeartbeat {
    private final FullIngestionStore store;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public AutoCloseable start(UUID repo, UUID owner, long leaseSeconds) {
        Thread worker = Thread.currentThread();
        Object lifecycle = new Object();
        boolean[] stopped = {false};
        long interval = Math.max(100, leaseSeconds * 1000 / 3);
        ScheduledFuture<?> renewal = scheduler.scheduleAtFixedRate(() -> {
            synchronized (lifecycle) {
                if (stopped[0]) return;
                try {
                    store.renew(repo, owner, leaseSeconds);
                } catch (RuntimeException e) {
                    log.warn("Cannot renew ingestion lease for {}", repo, e);
                    worker.interrupt();
                    throw e;
                }
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        return () -> {
            synchronized (lifecycle) {
                // A completed callback cannot interrupt this pooled thread's next job.
                stopped[0] = true;
                renewal.cancel(false);
            }
        };
    }

    @PreDestroy void shutdown() { scheduler.shutdownNow(); }
}
