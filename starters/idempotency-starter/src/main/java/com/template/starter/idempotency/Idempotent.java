package com.template.starter.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as idempotent. Requests must include an
 * {@code Idempotency-Key} header (configurable). On duplicate keys the
 * previously cached response is replayed without re-executing the handler.
 * <p>
 * Only 2xx responses are cached. Error responses are never stored so
 * the client can retry safely.
 *
 * <pre>{@code
 * @PostMapping
 * @Idempotent
 * public ResponseEntity<ApiResponse<Order>> create(@RequestBody CreateOrderRequest req) { ... }
 *
 * @PostMapping("/charge")
 * @Idempotent(ttlSeconds = 3600) // cache for 1 hour instead of default 24h
 * public ResponseEntity<ApiResponse<Payment>> charge(@RequestBody ChargeRequest req) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Per-endpoint TTL override in seconds. {@code -1} means use the global
     * default from {@code acme.idempotency.default-ttl-seconds}.
     */
    int ttlSeconds() default -1;
}
