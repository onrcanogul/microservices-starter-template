package com.template.kafka.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Starter-level knobs for retry/DLT and deserialization. */
@ConfigurationProperties(prefix = "acme.messaging.kafka")
public class KafkaMessagingProperties {
    /** Max attempts including first try. */
    private int maxAttempts = 5;
    /** Backoff between retries (ms). */
    private long backoffMs = 200;
    /** Dead letter topic suffix. */
    private String dltSuffix = ".DLT";
    /** Trusted packages for JsonDeserializer. */
    private String trustedPackages = "*";

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public long getBackoffMs() { return backoffMs; }
    public void setBackoffMs(long backoffMs) { this.backoffMs = backoffMs; }
    public String getDltSuffix() { return dltSuffix; }
    public void setDltSuffix(String dltSuffix) { this.dltSuffix = dltSuffix; }
    public String getTrustedPackages() { return trustedPackages; }
    public void setTrustedPackages(String trustedPackages) { this.trustedPackages = trustedPackages; }
}
