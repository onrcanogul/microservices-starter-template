package com.template.messaging.saga;

/**
 * Lifecycle states of a saga instance.
 * <p>
 * State transitions:
 * <pre>
 *   STARTED → RUNNING → COMPLETED
 *                     → COMPENSATING → FAILED
 *                                    → COMPENSATED
 * </pre>
 */
public enum SagaStatus {
    STARTED,
    RUNNING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED
}
