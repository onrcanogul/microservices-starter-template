package com.template.starter.saga.choreography;

import java.util.UUID;

/**
 * ThreadLocal-based holder for saga correlation context.
 * Carries the saga ID and correlation ID across event handlers within the same thread.
 * <p>
 * Usage in choreography-based sagas:
 * <pre>{@code
 * SagaContextHolder.set(sagaId, correlationId);
 * try {
 *     // process event — downstream calls can access context
 *     orderService.createOrder(event);
 * } finally {
 *     SagaContextHolder.clear();
 * }
 * }</pre>
 */
public final class SagaContextHolder {

    private SagaContextHolder() {}

    private static final ThreadLocal<SagaContext> CONTEXT = new ThreadLocal<>();

    public record SagaContext(UUID sagaId, UUID correlationId) {}

    public static void set(UUID sagaId, UUID correlationId) {
        CONTEXT.set(new SagaContext(sagaId, correlationId));
    }

    public static SagaContext get() {
        return CONTEXT.get();
    }

    public static UUID correlationId() {
        SagaContext ctx = CONTEXT.get();
        return ctx != null ? ctx.correlationId() : null;
    }

    public static UUID sagaId() {
        SagaContext ctx = CONTEXT.get();
        return ctx != null ? ctx.sagaId() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
