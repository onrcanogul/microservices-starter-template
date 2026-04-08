package com.template.starter.saga.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "acme.saga")
public class SagaProperties {

    /** Enable/disable the saga orchestration engine. */
    private boolean enabled = true;

    /** Default timeout for saga completion before it is considered stuck. */
    private Duration timeout = Duration.ofMinutes(30);

    /** Maximum retry attempts for a stuck saga before marking it FAILED. */
    private int maxRetries = 3;

    /** Polling interval for the saga recovery scheduler. */
    private Duration schedulerRate = Duration.ofSeconds(30);

    /** Cleanup configuration for completed/failed sagas. */
    private Cleanup cleanup = new Cleanup();

    public static class Cleanup {
        /** Cron expression for saga cleanup job. */
        private String cron = "0 0 4 * * *";

        /** Retention period for completed/failed sagas. */
        private Duration retention = Duration.ofDays(30);

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public Duration getRetention() { return retention; }
        public void setRetention(Duration retention) { this.retention = retention; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getSchedulerRate() { return schedulerRate; }
    public void setSchedulerRate(Duration schedulerRate) { this.schedulerRate = schedulerRate; }
    public Cleanup getCleanup() { return cleanup; }
    public void setCleanup(Cleanup cleanup) { this.cleanup = cleanup; }
}
