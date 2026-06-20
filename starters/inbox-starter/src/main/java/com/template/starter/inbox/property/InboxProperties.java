package com.template.starter.inbox.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for inbox processing (acme.inbox.*). Mirrors the SagaProperties style:
 * plain getters/setters, Duration fields, nested static config classes.
 */
@ConfigurationProperties(prefix = "acme.inbox")
public class InboxProperties {

    /** Max rows fetched per processing cycle. */
    private int batchSize = 100;

    private Scheduler scheduler = new Scheduler();
    private Cleanup cleanup = new Cleanup();
    private Retry retry = new Retry();

    public static class Scheduler {
        /** Poll interval (also read directly by @Scheduled via the acme.inbox.scheduler.rate key). */
        private Duration rate = Duration.ofMillis(1500);
        public Duration getRate() { return rate; }
        public void setRate(Duration rate) { this.rate = rate; }
    }

    public static class Cleanup {
        private String cron = "0 0 3 * * *";
        /** Delete processed/published rows older than this many days (key: cleanup.retention-days). */
        private int retentionDays = 7;
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    /** Processing-layer retry/backoff before a message is dead-lettered. */
    public static class Retry {
        /** Total processing attempts before a message is marked dead. */
        private int maxAttempts = 5;
        /** Base backoff; effective delay = min(maxBackoff, backoff * 2^(attempts-1)). */
        private Duration backoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofMinutes(5);
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getBackoff() { return backoff; }
        public void setBackoff(Duration backoff) { this.backoff = backoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }
    public Cleanup getCleanup() { return cleanup; }
    public void setCleanup(Cleanup cleanup) { this.cleanup = cleanup; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
}
