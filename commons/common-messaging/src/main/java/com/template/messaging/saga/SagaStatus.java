package com.template.messaging.saga;

/**
 * Lifecycle states of a saga instance.
 * <p>
 * State transitions:
 * <pre>
 *   STARTED → RUNNING → COMPLETED
 *                     → WAITING_FOR_REPLY → RUNNING → ...        (async step: suspend then resume)
 *                                         → COMPENSATING         (reply failed / timed out)
 *                     → COMPENSATING → FAILED
 *                                    → COMPENSATED
 * </pre>
 */
public enum SagaStatus {
    STARTED,
    RUNNING,
    /** An async step published its request and the saga is parked awaiting the correlated reply. */
    WAITING_FOR_REPLY,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED
}
