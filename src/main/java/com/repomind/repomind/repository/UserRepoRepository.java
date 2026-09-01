package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.model.entity.UserRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepoRepository extends JpaRepository<UserRepo, UUID> {

    // Get all repos a specific user has access to
    // Used in IngestionController to replace the old findAll() call
    @Query("SELECT ur.repo FROM UserRepo ur WHERE ur.user.id= :userId ORDER BY ur.addedAt DESC")
    List<RepoEntity> findReposByUserId(@Param("userId") UUID userId);

    // Check if a user already has access to a specific repo
    // Used during dedup: if user submits a URL they already have, return existing
    boolean existsByUserIdAndRepoId(UUID userId, UUID repoId);

    long countByUserId(UUID userId);
    long countByRepoId(UUID repoId);
    // Find the join record — used when we need to delete access
    Optional<UserRepo> findByUserIdAndRepoId(UUID userId, UUID repoId);
}
