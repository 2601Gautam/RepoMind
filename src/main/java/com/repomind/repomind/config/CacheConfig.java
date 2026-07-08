package com.repomind.repomind.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager()
    {
        // Simple in-memory cache manager — good for a single-instance deployment
        return new ConcurrentMapCacheManager();
    }

}
