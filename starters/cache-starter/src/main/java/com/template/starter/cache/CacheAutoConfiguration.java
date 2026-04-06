package com.template.starter.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.template.starter.cache.property.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for Redis-backed Spring Cache with JSON serialization.
 * <p>
 * Activated when {@code spring-data-redis} is on the classpath and
 * {@code acme.cache.enabled} is not explicitly set to {@code false}.
 * <p>
 * Features:
 * <ul>
 *   <li>JSON serialization with type info (safe deserialization)</li>
 *   <li>Configurable global TTL and per-cache TTL overrides</li>
 *   <li>Key prefix for namespace isolation in shared Redis instances</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({RedisConnectionFactory.class, CacheManager.class})
@ConditionalOnProperty(prefix = "acme.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
@EnableCaching
public class CacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     CacheProperties props) {
        RedisCacheConfiguration defaultConfig = createDefaultConfig(props);

        Map<String, RedisCacheConfiguration> perCacheConfigs = new HashMap<>();
        props.getTtlOverrides().forEach((cacheName, ttl) -> {
            perCacheConfigs.put(cacheName, defaultConfig.entryTtl(ttl));
            log.info("Cache [{}]: TTL override = {}", cacheName, ttl);
        });

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfigs)
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration createDefaultConfig(CacheProperties props) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.getDefaultTtl())
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        if (props.isUseKeyPrefix()) {
            config = config.prefixCacheNameWith(props.getKeyPrefix());
        } else {
            config = config.disableKeyPrefix();
        }

        return config;
    }
}
