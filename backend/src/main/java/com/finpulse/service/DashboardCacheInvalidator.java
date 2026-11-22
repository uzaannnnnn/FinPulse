package com.finpulse.service;

import com.finpulse.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User-isolated dashboard cache invalidator executing after DB commit to prevent stale-read repopulation races.
 */
@Component
public class DashboardCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(DashboardCacheInvalidator.class);

    private final CacheManager cacheManager;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    public DashboardCacheInvalidator(
            CacheManager cacheManager,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider
    ) {
        this.cacheManager = cacheManager;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    public void evictAfterCommit(UUID userId) {
        if (userId == null) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictUserDashboardCache(userId);
                }
            });
        } else {
            evictUserDashboardCache(userId);
        }
    }

    public void evictUserDashboardCache(UUID userId) {
        if (userId == null) {
            return;
        }

        try {
            Cache cache = cacheManager.getCache(CacheConfig.DASHBOARD_SUMMARY_CACHE);
            if (cache == null) {
                return;
            }

            String userKeyPrefix = userId + ":";
            if (cache instanceof ConcurrentMapCache concurrentMapCache) {
                concurrentMapCache.getNativeCache().keySet().removeIf(
                        key -> key != null && key.toString().startsWith(userKeyPrefix)
                );
                return;
            }

            if (cache instanceof RedisCache) {
                evictFromRedisByScan(userId);
            }
        } catch (Exception ex) {
            log.warn("Failed to evict dashboard summary cache for userId={}: {}", userId, ex.getMessage());
        }
    }

    private void evictFromRedisByScan(UUID userId) {
        RedisConnectionFactory connectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return;
        }

        String pattern = "*" + CacheConfig.DASHBOARD_SUMMARY_CACHE + "::" + userId + ":*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();

        try (RedisConnection connection = connectionFactory.getConnection();
             Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            List<byte[]> keysToDelete = new ArrayList<>();
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
                if (keysToDelete.size() >= 200) {
                    connection.keyCommands().del(keysToDelete.toArray(byte[][]::new));
                    keysToDelete.clear();
                }
            }
            if (!keysToDelete.isEmpty()) {
                connection.keyCommands().del(keysToDelete.toArray(byte[][]::new));
            }
        }
    }
}
