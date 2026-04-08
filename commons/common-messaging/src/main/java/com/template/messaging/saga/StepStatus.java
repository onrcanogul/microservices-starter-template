package com.template.messaging.saga;

/**
 * Execution states of an individual saga step.
 */
public enum StepStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    COMPENSATED,
    SKIPPED
}
