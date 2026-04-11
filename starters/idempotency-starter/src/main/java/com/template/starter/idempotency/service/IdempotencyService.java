package com.template.starter.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.starter.idempotency.model.CachedResponse;
import com.template.starter.idempotency.property.IdempotencyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed service for idempotency key management.
 * <p>
 * Handles three operations:
 * <ol>
 *   <li><b>Get</b> — retrieve a previously cached response</li>
 *   <li><b>Store</b> — cache a response with TTL</li>
 *   <li><b>Lock</b> — acquire a short-lived distributed lock to prevent concurrent duplicates</li>
 * </ol>
 * <p>
 * The lock implementation uses UUID ownership tokens and a Lua script for
 * atomic check-and-delete, preventing one thread from accidentally releasing
 * another thread's lock if the original lock expired.
 */
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String LOCK_SUFFIX = "lock:";

    /** Lua script: atomically deletes the key only if the stored value matches the provided owner token. */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else return 0 end");
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;
    private final ThreadLocal<String> lockOwner = new ThreadLocal<>();

    public IdempotencyService(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              IdempotencyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Retrieve a cached response for the given idempotency key.
     *
     * @return the cached response, or empty if not found
     */
    public Optional<CachedResponse> get(String idempotencyKey) {
        String redisKey = responseKey(idempotencyKey);
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CachedResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached response for key [{}], treating as miss", idempotencyKey, e);
            redisTemplate.delete(redisKey);
            return Optional.empty();
        }
    }

    /**
     * Cache a response for the given idempotency key.
     *
     * @param ttlSeconds TTL override; uses global default if {@code <= 0}
     */
    public void store(String idempotencyKey, CachedResponse response, long ttlSeconds) {
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : properties.getDefaultTtlSeconds();
        String redisKey = responseKey(idempotencyKey);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(effectiveTtl));
            log.debug("Cached idempotent response for key [{}], TTL={}s", idempotencyKey, effectiveTtl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response for idempotency key [{}]", idempotencyKey, e);
        }
    }

    /**
     * Attempt to acquire a distributed lock for the given key.
     * Stores a unique owner token so only the acquiring thread can release it.
     *
     * @return {@code true} if the lock was acquired
     */
    public boolean tryLock(String idempotencyKey) {
        String lockKey = lockKey(idempotencyKey);
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, owner, Duration.ofSeconds(properties.getLockTtlSeconds()));
        if (Boolean.TRUE.equals(acquired)) {
            lockOwner.set(owner);
            return true;
        }
        return false;
    }

    /**
     * Release the distributed lock for the given key.
     * Uses a Lua script to atomically verify ownership before deletion,
     * preventing accidental release of another thread's lock after TTL expiry.
     */
    public void unlock(String idempotencyKey) {
        String owner = lockOwner.get();
        lockOwner.remove();
        if (owner != null) {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey(idempotencyKey)), owner);
        }
    }

    private String responseKey(String idempotencyKey) {
        return properties.getKeyPrefix() + idempotencyKey;
    }

    private String lockKey(String idempotencyKey) {
        return properties.getKeyPrefix() + LOCK_SUFFIX + idempotencyKey;
    }
}
