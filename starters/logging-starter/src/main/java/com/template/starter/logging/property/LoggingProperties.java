package com.template.starter.logging.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme.logging")
public class LoggingProperties {

    /**
     * Whether the logging starter is enabled.
     */
    private boolean enabled = true;

    /**
     * MDC filter configuration.
     */
    private Mdc mdc = new Mdc();

    public static class Mdc {
        /**
         * Whether the MDC propagation filter is enabled.
         */
        private boolean enabled = true;

        /**
         * Header name for user ID propagation.
         */
        private String userIdHeader = "X-User-Id";

        /**
         * Header name for user email propagation.
         */
        private String userEmailHeader = "X-User-Email";

        /**
         * Header name for correlation ID propagation.
         */
        private String correlationIdHeader = "X-Correlation-Id";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUserIdHeader() { return userIdHeader; }
        public void setUserIdHeader(String userIdHeader) { this.userIdHeader = userIdHeader; }
        public String getUserEmailHeader() { return userEmailHeader; }
        public void setUserEmailHeader(String userEmailHeader) { this.userEmailHeader = userEmailHeader; }
        public String getCorrelationIdHeader() { return correlationIdHeader; }
        public void setCorrelationIdHeader(String correlationIdHeader) { this.correlationIdHeader = correlationIdHeader; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Mdc getMdc() { return mdc; }
    public void setMdc(Mdc mdc) { this.mdc = mdc; }
}
