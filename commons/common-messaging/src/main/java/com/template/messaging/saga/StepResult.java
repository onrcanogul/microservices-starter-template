package com.template.messaging.saga;

/**
 * Result of executing or compensating a saga step.
 * Sealed to {@link Success} and {@link Failure} — forces callers to handle both cases.
 */
public sealed interface StepResult permits StepResult.Success, StepResult.Failure {

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
}
