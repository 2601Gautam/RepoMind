package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepoJpaRepository extends JpaRepository<RepoEntity, UUID> {

    // Spring reads this method name and generates:
    // SELECT * FROM repositories ORDER BY created_at DESC
    // No SQL needed — the method name is the query
    List<RepoEntity> findAllByOrderByCreatedAtDesc();

    // THIS must exist — used for dedup check in ingest endpoint
    Optional<RepoEntity> findFirstByGithubUrlOrderByCreatedAtDesc(String githubUrl);

    /**
     * Atomically claims a queued job. Standard SQS provides at-least-once
     * delivery, so the same message can occasionally be delivered twice.
     * Only one worker may change a PENDING repository to PROCESSING. A stale
     * PROCESSING lease can be reclaimed after a worker crash.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update RepoEntity r
           set r.status = :processingStatus,
               r.ingestionLeaseUntil = :leaseUntil,
               r.errorMessage = null
         where r.id = :repoId
           and (
               r.status = :pendingStatus
               or (
                   r.status = :processingStatus
                   and (r.ingestionLeaseUntil is null or r.ingestionLeaseUntil < :now)
               )
           )
        """)
    int claimIngestionJob(
            @Param("repoId") UUID repoId,
            @Param("pendingStatus") RepoEntity.IngestionStatus pendingStatus,
            @Param("processingStatus") RepoEntity.IngestionStatus processingStatus,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    /**
     * Atomically claims an incremental sync.  A read of {@code syncing}
     * followed by a later write is not safe: two HTTP requests can both read
     * false before either async worker stores true.  The conditional update is
     * the compare-and-set operation, so it is safe across application nodes as
     * well as across threads in one node.
     *
     * Full ingestion deliberately does not use this method.  It has its own
     * PENDING/PROCESSING lease claim above because it is driven by SQS.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update RepoEntity r
           set r.syncing = true,
               r.syncMessage = :syncMessage
         where r.id = :repoId
           and r.status = :readyStatus
           and r.syncing = false
        """)
    int claimIncrementalSync(
            @Param("repoId") UUID repoId,
            @Param("readyStatus") RepoEntity.IngestionStatus readyStatus,
            @Param("syncMessage") String syncMessage
    );

    /**
     * Releases a successfully claimed incremental sync.  This is a bulk
     * update so it cannot overwrite progress fields saved by the worker.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update RepoEntity r
           set r.syncing = false
         where r.id = :repoId
        """)
    int releaseIncrementalSync(@Param("repoId") UUID repoId);

}
