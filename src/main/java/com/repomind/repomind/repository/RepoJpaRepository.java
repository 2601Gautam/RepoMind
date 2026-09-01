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

    // THIS must exist — used to find duplicate rows for same URL
    List<RepoEntity> findByGithubUrlAndIdNot(String githubUrl, UUID excludeId);

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

}
