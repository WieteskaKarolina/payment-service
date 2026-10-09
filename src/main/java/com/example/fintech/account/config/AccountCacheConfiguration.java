package com.example.fintech.account.config;

import com.example.fintech.account.dto.AccountResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class AccountCacheConfiguration implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AccountCacheConfiguration.class);

    @Bean
    RedisCacheConfiguration redisCacheConfiguration(ObjectMapper objectMapper) {
        var accountListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, AccountResponse.class);
        var valueSerializer = new JacksonJsonRedisSerializer<>(objectMapper, accountListType);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(2))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
                );
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed for cache '{}' and key '{}'; loading from PostgreSQL",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception, Cache cache, Object key, Object value
            ) {
                log.warn("Cache write failed for cache '{}' and key '{}'; returning database result",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache eviction failed for cache '{}' and key '{}'; entries will expire by TTL",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed for cache '{}'; entries will expire by TTL",
                        cache.getName(), exception);
            }
        };
    }
}
