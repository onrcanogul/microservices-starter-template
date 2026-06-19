package com.template.messaging.saga;

/**
 * Result of executing or compensating a saga step.
 * Sealed to {@link Success}, {@link Failure} and {@link Suspended} — forces callers to handle each case.
 */
public sealed interface StepResult permits StepResult.Success, StepResult.Failure, StepResult.Suspended {

    record Success(String output) implements StepResult {
        public Success() {
            this(null);
        }
    }

    record Failure(String reason, Exception cause) implements StepResult {
        public Failure(String reason) {
            this(reason, null);
        }
    }

    /**
     * The step published its request and is now awaiting an asynchronous reply, correlated by
     * {@code correlationKey}. The orchestrator persists {@code WAITING_FOR_REPLY} and releases the
     * calling thread; {@code SagaOrchestrator.resumeWithReply(correlationKey, reply)} resumes the saga.
     */
    record Suspended(String correlationKey) implements StepResult {}

    static StepResult success() {
        return new Success();
    }

    static StepResult success(String output) {
        return new Success(output);
    }

    static StepResult failure(String reason) {
        return new Failure(reason);
    }

    static StepResult failure(String reason, Exception cause) {
        return new Failure(reason, cause);
    }

    static StepResult suspended(String correlationKey) {
        return new Suspended(correlationKey);
    }
}
