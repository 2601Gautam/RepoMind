package com.repomind.repomind.controller;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

// Single responsibility: manages cache invalidation
// Separated from IngestionService to avoid circular dependencies
// and keep IngestionService focused on ingestion only
@Service
public class CacheService {

    // Evicts all entries from userRepos cache
    // Called after any repo status change so users see fresh data
    @CacheEvict(value = "userRepos", allEntries = true)
    public void evictUserReposCache() {
        // AOP handles the actual eviction — method body intentionally empty
    }
}