package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

}
