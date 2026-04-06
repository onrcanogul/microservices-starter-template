package com.template.starter.resilience.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme.resilience")
public class ResilienceProperties {

    /** Log CircuitBreaker state transitions (CLOSED → OPEN → HALF_OPEN). */
    private boolean logStateTransitions = true;

    /** Log each retry attempt with the cause of the failure. */
    private boolean logRetryAttempts = true;

    public boolean isLogStateTransitions() { return logStateTransitions; }
    public void setLogStateTransitions(boolean logStateTransitions) { this.logStateTransitions = logStateTransitions; }

    public boolean isLogRetryAttempts() { return logRetryAttempts; }
    public void setLogRetryAttempts(boolean logRetryAttempts) { this.logRetryAttempts = logRetryAttempts; }
}
