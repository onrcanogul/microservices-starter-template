package com.template.starter.webclient.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "acme.webclient")
public class WebClientProperties {

    /** Default connection timeout for all outbound HTTP calls. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Default read (response) timeout for all outbound HTTP calls. */
    private Duration readTimeout = Duration.ofSeconds(10);

    /** Resilience4j integration settings. */
    private Resilience resilience = new Resilience();

    /** Header propagation settings. */
    private HeaderPropagation headerPropagation = new HeaderPropagation();

    public static class Resilience {

        /** Enable Resilience4j circuit breaker + retry wrapping on RestClient calls. */
        private boolean enabled = true;

        /** Default circuit breaker instance name prefix. */
        private String circuitBreakerPrefix = "restclient";

        /** Default retry instance name prefix. */
        private String retryPrefix = "restclient";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCircuitBreakerPrefix() { return circuitBreakerPrefix; }
        public void setCircuitBreakerPrefix(String circuitBreakerPrefix) { this.circuitBreakerPrefix = circuitBreakerPrefix; }
        public String getRetryPrefix() { return retryPrefix; }
        public void setRetryPrefix(String retryPrefix) { this.retryPrefix = retryPrefix; }
    }

    public static class HeaderPropagation {

        /** Propagate X-User-Id and X-User-Email headers from the incoming request to outbound calls. */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    // ── getters / setters ───────────────────────────────────────────────

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    public Resilience getResilience() { return resilience; }
    public void setResilience(Resilience resilience) { this.resilience = resilience; }

    public HeaderPropagation getHeaderPropagation() { return headerPropagation; }
    public void setHeaderPropagation(HeaderPropagation headerPropagation) { this.headerPropagation = headerPropagation; }
}
