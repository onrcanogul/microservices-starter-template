package com.template.web.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls how errors are rendered on HTTP responses.
 */
@ConfigurationProperties(prefix = "acme.web.error")
public class WebErrorProperties {

    public enum Format { API_RESPONSE, PROBLEM_DETAIL }

    /** Which format to use for error bodies. Default: API_RESPONSE. */
    private Format format = Format.API_RESPONSE;

    /** Include exception message in non-5xx errors (useful for dev). */
    private boolean includeMessage = true;

    /** Include stack traces in 5xx responses (dev-only recommendation). */
    private boolean includeStackTrace = false;

    public Format getFormat() { return format; }
    public void setFormat(Format format) { this.format = format; }

    public boolean isIncludeMessage() { return includeMessage; }
    public void setIncludeMessage(boolean includeMessage) { this.includeMessage = includeMessage; }

    public boolean isIncludeStackTrace() { return includeStackTrace; }
    public void setIncludeStackTrace(boolean includeStackTrace) { this.includeStackTrace = includeStackTrace; }
}
