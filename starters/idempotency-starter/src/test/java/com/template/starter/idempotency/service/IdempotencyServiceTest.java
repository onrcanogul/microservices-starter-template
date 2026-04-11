package com.template.starter.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.starter.idempotency.model.CachedResponse;
import com.template.starter.idempotency.property.IdempotencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdempotencyProperties properties;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        properties = new IdempotencyProperties();
        properties.setKeyPrefix("idempotency:");
        properties.setDefaultTtlSeconds(86400);
        properties.setLockTtlSeconds(30);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new IdempotencyService(redisTemplate, objectMapper, properties);
    }

    @Test
    void get_existingKey_returnsCachedResponse() throws Exception {
        CachedResponse expected = new CachedResponse(200, "application/json", "{\"data\":\"ok\"}", Map.of());
        String json = objectMapper.writeValueAsString(expected);
        when(valueOps.get("idempotency:abc123")).thenReturn(json);

        Optional<CachedResponse> result = service.get("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(200);
        assertThat(result.get().body()).isEqualTo("{\"data\":\"ok\"}");
    }

    @Test
    void get_missingKey_returnsEmpty() {
        when(valueOps.get("idempotency:unknown")).thenReturn(null);

        Optional<CachedResponse> result = service.get("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void get_corruptedJson_deletesKeyAndReturnsEmpty() {
        when(valueOps.get("idempotency:corrupt")).thenReturn("not-json");
        when(redisTemplate.delete("idempotency:corrupt")).thenReturn(true);

        Optional<CachedResponse> result = service.get("corrupt");

        assertThat(result).isEmpty();
        verify(redisTemplate).delete("idempotency:corrupt");
    }

    @Test
    void store_cachesResponseWithGlobalTtl() throws Exception {
        CachedResponse response = new CachedResponse(201, "application/json", "{\"id\":1}", Map.of("Location", "/api/orders/1"));

        service.store("key1", response, -1);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("idempotency:key1"), valueCaptor.capture(), eq(Duration.ofSeconds(86400)));
        CachedResponse stored = objectMapper.readValue(valueCaptor.getValue(), CachedResponse.class);
        assertThat(stored.status()).isEqualTo(201);
    }

    @Test
    void store_cachesResponseWithCustomTtl() {
        CachedResponse response = new CachedResponse(200, "application/json", "{}", Map.of());

        service.store("key2", response, 3600);

        verify(valueOps).set(eq("idempotency:key2"), anyString(), eq(Duration.ofSeconds(3600)));
    }

    @Test
    void tryLock_acquiresLock_returnsTrue() {
        when(valueOps.setIfAbsent(eq("idempotency:lock:key1"), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        assertThat(service.tryLock("key1")).isTrue();
    }

    @Test
    void tryLock_lockAlreadyHeld_returnsFalse() {
        when(valueOps.setIfAbsent(eq("idempotency:lock:key1"), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(false);

        assertThat(service.tryLock("key1")).isFalse();
    }

    @Test
    void unlock_executesLuaScript() {
        // First acquire the lock to populate the ThreadLocal owner
        when(valueOps.setIfAbsent(eq("idempotency:lock:key1"), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        service.tryLock("key1");

        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);

        service.unlock("key1");

        verify(redisTemplate).execute(any(), anyList(), any());
    }

    @Test
    void store_usesConfiguredKeyPrefix() {
        properties.setKeyPrefix("custom:");
        CachedResponse response = new CachedResponse(200, "application/json", "{}", Map.of());

        service.store("key3", response, -1);

        verify(valueOps).set(eq("custom:key3"), anyString(), any(Duration.class));
    }
}
