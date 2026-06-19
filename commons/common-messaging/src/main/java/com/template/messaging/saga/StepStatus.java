package com.template.messaging.saga;

/**
 * Execution states of an individual saga step.
 */
public enum StepStatus {
    PENDING,
    /** Step published its request and is awaiting an async reply (see {@code AsyncSagaStepHandler}). */
    AWAITING,
    SUCCEEDED,
    FAILED,
    COMPENSATED,
    SKIPPED
}
