package com.template.core.error;

/**
 * Common, reusable error codes for typical API situations.
 */
public enum StandardErrorCodes implements ErrorCode {
    VALIDATION_FAILED("validation_failed", 400),
    NOT_FOUND("not_found", 404),
    CONFLICT("conflict", 409),
    UNAUTHORIZED("unauthorized", 401),
    FORBIDDEN("forbidden", 403),
    RATE_LIMITED("rate_limited", 429),
    INTERNAL_ERROR("internal_error", 500),
    SERVICE_UNAVAILABLE("service_unavailable", 503),
    TIMEOUT("timeout", 504);

    private final String code;
    private final int http;

    StandardErrorCodes(String code, int http) { this.code = code; this.http = http; }
    @Override public String code() { return code; }
    @Override public int httpStatus() { return http; }
}

