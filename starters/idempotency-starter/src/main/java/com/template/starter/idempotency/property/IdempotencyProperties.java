package com.template.starter.idempotency.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for HTTP idempotency support.
 * <p>
 * All properties live under the {@code acme.idempotency.*} namespace.
 */
@ConfigurationProperties(prefix = "acme.idempotency")
public class IdempotencyProperties {

    /** Enable/disable the idempotency filter globally. */
    private boolean enabled = true;

    /** HTTP header name that carries the idempotency key. */
    private String headerName = "Idempotency-Key";

    /** Redis key prefix for namespace isolation in shared Redis instances. */
    private String keyPrefix = "idempotency:";

    /** Default TTL for cached responses, in seconds. */
    private long defaultTtlSeconds = 86400; // 24 hours

    /** TTL for the distributed lock that prevents concurrent duplicate processing, in seconds. */
    private long lockTtlSeconds = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public long getDefaultTtlSeconds() { return defaultTtlSeconds; }
    public void setDefaultTtlSeconds(long defaultTtlSeconds) { this.defaultTtlSeconds = defaultTtlSeconds; }

    public long getLockTtlSeconds() { return lockTtlSeconds; }
    public void setLockTtlSeconds(long lockTtlSeconds) { this.lockTtlSeconds = lockTtlSeconds; }
}
