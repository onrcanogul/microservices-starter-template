package com.template.core.response;

import lombok.Getter;

import java.util.Map;
import java.util.Objects;

/**
 * Minimal, framework-agnostic API response wrapper.
 * Either `success=true + data` or `success=false + error`.
 * Can be returned directly from controllers in a web layer.
 */
@Getter
public final class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final ApiError error;

    private ApiResponse(boolean success, T data, ApiError error) {
        this.success = success; this.data = data; this.error = error;
    }

    /** Success response with payload. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** Error response with code and message (no details). */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, null, null));
    }

    /** Error response with code, message, optional details and trace id. */
    public static <T> ApiResponse<T> error(String code, String message, Map<String, Object> details, String traceId) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details, traceId));
    }

    /** Nested error description. */
    @Getter
    public static final class ApiError {
        private final String code;
        private final String message;
        private final Map<String, Object> details;
        private final String traceId;

        /**
         * @param code     stable machine code (e.g., "conflict")
         * @param message  human-readable message (optional if you use i18n on the client)
         * @param details  optional structured details (e.g., validation errors)
         * @param traceId  optional trace id for correlating with logs/traces
         */
        public ApiError(String code, String message, Map<String, Object> details, String traceId) {
            this.code = Objects.requireNonNull(code, "code");
            this.message = message;
            this.details = details;
            this.traceId = traceId;
        }
    }
}

