package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.repomind.repomind.model.entity.RepoEntity.IngestionStatus.PENDING;
import static com.repomind.repomind.model.entity.RepoEntity.IngestionStatus.PROCESSING;

@Service
@RequiredArgsConstructor
public class FullIngestionStore {
    private final RepoJpaRepository repos;
    private final CodeChunkRepository chunks;

    public static class LeaseBusy extends IllegalStateException {
        private final LocalDateTime until;

        LeaseBusy(LocalDateTime until) {
            super("An ingestion worker still owns this job");
            this.until = until;
        }

        public int retryAfterSeconds() {
            return (int) Math.max(1, Math.min(43200, Duration.between(LocalDateTime.now(), until).getSeconds() + 1));
        }
    }

    static class OwnershipLost extends IllegalStateException {
        OwnershipLost(UUID id) { super("Ingestion lease superseded for " + id); }
    }

    @Transactional
    public Optional<RepoEntity> claim(UUID id, long leaseSeconds) {
        if (leaseSeconds <= 0) throw new IllegalArgumentException("Ingestion lease must be positive");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        int acquired = repos.claimIngestionJob(id, PENDING, PROCESSING, now, now.plusSeconds(leaseSeconds));
        Optional<RepoEntity> row = repos.findById(id);
        if (acquired == 0) {
            // Only terminal or deleted jobs may be acknowledged as duplicates.
            row.filter(repo -> repo.getStatus() == PROCESSING)
                    .ifPresent(repo -> { throw new LeaseBusy(repo.getIngestionLeaseUntil()); });
            return Optional.empty();
        }
        RepoEntity repo = row.orElseThrow();
        // The claim holds the row lock. Discard a crashed attempt's partial index before replay.
        chunks.deleteByRepositoryId(id);
        repo.setTotalFiles(0);
        repo.setTotalChunks(0);
        repo.setProcessedFiles(0);
        repo.setIngestionOwner(UUID.randomUUID());
        return Optional.of(repos.save(repo));
    }

    @Transactional
    public void appendBatch(UUID id, UUID owner, List<CodeChunk> batch) {
        requireOwner(id, owner);
        chunks.saveAll(batch);
    }

    @Transactional
    public void saveProgress(RepoEntity repo, UUID owner) {
        RepoEntity current = requireOwner(repo.getId(), owner);
        if (repo.getStatus() == PROCESSING) {
            repo.setIngestionLeaseUntil(current.getIngestionLeaseUntil());
        } else {
            repo.setIngestionOwner(null);
            repo.setIngestionLeaseUntil(null);
        }
        repos.save(repo);
    }

    @Transactional
    public void releaseForRetry(UUID id, UUID owner, String error) {
        RepoEntity repo = requireOwner(id, owner);
        repo.setStatus(PENDING);
        repo.setErrorMessage(error);
        repo.setSyncMessage("Ingestion attempt failed; awaiting queue retry");
        repo.setIngestionOwner(null);
        repo.setIngestionLeaseUntil(null);
    }

    @Transactional
    public void renew(UUID id, UUID owner, long seconds) {
        RepoEntity repo = requireOwner(id, owner);
        repo.setIngestionLeaseUntil(LocalDateTime.now().plusSeconds(seconds));
    }

    private RepoEntity requireOwner(UUID id, UUID owner) {
        RepoEntity current = repos.lockForIndexUpdate(id).orElseThrow(() -> new OwnershipLost(id));
        if (current.getStatus() != PROCESSING || !owner.equals(current.getIngestionOwner())
                || current.getIngestionLeaseUntil() == null
                || !current.getIngestionLeaseUntil().isAfter(LocalDateTime.now()))
            throw new OwnershipLost(id);
        return current;
    }
}
