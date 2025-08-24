package com.template.core.exception;



import com.template.core.error.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework-neutral business exception.
 * Can be mapped to HTTP responses in a web layer (e.g., Problem+JSON).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> metadata;

    /** Create with code; message will default to the code value. */
    public BusinessException(ErrorCode code) {
        super(code.code());
        this.errorCode = code;
        this.metadata = Collections.emptyMap();
    }

    /** Create with explicit message. */
    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.errorCode = code;
        this.metadata = Collections.emptyMap();
    }

    /** Create with message and cause. */
    public BusinessException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = code;
        this.metadata = Collections.emptyMap();
    }

    /** Create with message and extra metadata (will be stored as an unmodifiable copy). */
    public BusinessException(ErrorCode code, String message, Map<String, Object> metadata) {
        super(message);
        this.errorCode = code;
        this.metadata = (metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata)));
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Map<String, Object> getMetadata() { return metadata; }

    /** Convenience to access the suggested HTTP status. */
    public int httpStatus() { return errorCode.httpStatus(); }

    /** Convenience to access the stable error code. */
    public String code() { return errorCode.code(); }

    /** Small factory helpers. */
    public static BusinessException of(ErrorCode code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException of(ErrorCode code, String message, Map<String, Object> metadata) {
        return new BusinessException(code, message, metadata);
    }
}
