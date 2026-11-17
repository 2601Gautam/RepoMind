package com.repomind.repomind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@Slf4j
public class RepositoryCacheConfig implements CachingConfigurer {
    @Bean
    RedisCacheManagerBuilderCustomizer immediateRepositoryCacheWrites(RedisConnectionFactory connection) {
        // A successful membership or status change must finish its eviction before the next list read.
        return builder -> builder.cacheWriter(RedisCacheWriter.create(connection, config -> config.immediateWrites()));
    }

    /**
     * The cache is an optimization, so a Redis failure must degrade to a database read instead of failing the request,
     * and must not fail an ingestion that already committed just because its final eviction could not reach Redis.
     * A failed eviction can leave a stale list until the five minute time-to-live expires.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException e, Cache cache, Object key) { warn("read", cache, e); }
            @Override public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) { warn("write", cache, e); }
            @Override public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) { warn("evict", cache, e); }
            @Override public void handleCacheClearError(RuntimeException e, Cache cache) { warn("clear", cache, e); }
            private void warn(String operation, Cache cache, RuntimeException e) {
                log.warn("Cache {} failed for '{}'; continuing without the cache: {}", operation, cache.getName(), e.getMessage());
            }
        };
    }
}
