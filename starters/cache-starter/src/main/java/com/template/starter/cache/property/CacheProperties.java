package com.template.starter.cache.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "acme.cache")
public class CacheProperties {

    /** Global TTL applied to all caches unless overridden. */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /**
     * Per-cache TTL overrides.
     * Key = cache name, Value = TTL duration.
     * Example: acme.cache.ttl-overrides.users=30m
     */
    private Map<String, Duration> ttlOverrides = Map.of();

    /** Prefix prepended to all cache keys (namespace isolation). */
    private String keyPrefix = "cache:";

    /** Whether to use the key prefix in Redis keys. */
    private boolean useKeyPrefix = true;

    /** Maximum number of in-flight Redis cache operations (connection pool). */
    private int maxPoolSize = 8;

    public Duration getDefaultTtl() { return defaultTtl; }
    public void setDefaultTtl(Duration defaultTtl) { this.defaultTtl = defaultTtl; }

    public Map<String, Duration> getTtlOverrides() { return ttlOverrides; }
    public void setTtlOverrides(Map<String, Duration> ttlOverrides) { this.ttlOverrides = ttlOverrides; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public boolean isUseKeyPrefix() { return useKeyPrefix; }
    public void setUseKeyPrefix(boolean useKeyPrefix) { this.useKeyPrefix = useKeyPrefix; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
}
