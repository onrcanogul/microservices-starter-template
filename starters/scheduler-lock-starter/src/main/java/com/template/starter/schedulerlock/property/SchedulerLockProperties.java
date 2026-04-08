package com.template.starter.schedulerlock.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the scheduler-lock-starter.
 * <p>
 * Controls ShedLock's Redis-backed distributed locking behavior.
 *
 * @see <a href="https://github.com/lukas-krecan/ShedLock">ShedLock</a>
 */
@ConfigurationProperties(prefix = "acme.scheduler-lock")
public class SchedulerLockProperties {

    /**
     * Enable/disable distributed scheduler locking.
     * When disabled, @SchedulerLock annotations are ignored and
     * scheduled tasks run on every instance.
     */
    private boolean enabled = true;

    /**
     * Default maximum lock duration. If a task takes longer than this,
     * the lock is released so other instances can pick it up.
     * Individual tasks can override via @SchedulerLock(lockAtMostFor).
     */
    private Duration defaultLockAtMost = Duration.ofMinutes(10);

    /**
     * Default minimum lock duration. Prevents the same task from
     * running on another node too quickly after the first execution.
     * Useful to prevent overlap when executions are very fast.
     */
    private Duration defaultLockAtLeast = Duration.ofSeconds(5);

    /**
     * Redis key prefix for ShedLock entries.
     * Useful for namespace isolation in shared Redis instances.
     */
    private String keyPrefix = "shedlock:";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getDefaultLockAtMost() { return defaultLockAtMost; }
    public void setDefaultLockAtMost(Duration defaultLockAtMost) { this.defaultLockAtMost = defaultLockAtMost; }

    public Duration getDefaultLockAtLeast() { return defaultLockAtLeast; }
    public void setDefaultLockAtLeast(Duration defaultLockAtLeast) { this.defaultLockAtLeast = defaultLockAtLeast; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
}
