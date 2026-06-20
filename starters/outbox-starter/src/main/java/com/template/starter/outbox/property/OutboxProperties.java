package com.template.starter.outbox.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for outbox publishing (acme.outbox.*). Same style as InboxProperties / SagaProperties.
 */
@ConfigurationProperties(prefix = "acme.outbox")
public class OutboxProperties {

    /** Max rows fetched per publish cycle. */
    private int batchSize = 100;
    /** Per-event publish timeout. */
    private Duration publishTimeout = Duration.ofSeconds(5);

    private Scheduler scheduler = new Scheduler();
    private Cleanup cleanup = new Cleanup();
    private Retry retry = new Retry();

    public static class Scheduler {
        /** Poll interval (also read directly by @Scheduled via the acme.outbox.scheduler.rate key). */
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

    /** Publish-layer retry/backoff before an event is dead-lettered. */
    public static class Retry {
        private int maxAttempts = 5;
        /** Base backoff; effective delay = min(maxBackoff, backoff * 2^(attempts-1)). */
        private Duration backoff = Duration.ofSeconds(2);
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
    public Duration getPublishTimeout() { return publishTimeout; }
    public void setPublishTimeout(Duration publishTimeout) { this.publishTimeout = publishTimeout; }
    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }
    public Cleanup getCleanup() { return cleanup; }
    public void setCleanup(Cleanup cleanup) { this.cleanup = cleanup; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
}
