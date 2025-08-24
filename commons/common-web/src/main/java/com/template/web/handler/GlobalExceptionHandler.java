package com.template.web.handler;

import com.template.core.exception.BusinessException;
import com.template.core.response.ApiResponse;
import com.template.core.tracing.TraceContext;
import com.template.web.property.WebErrorProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global exception mapping.
 * - BusinessException -> error code + http from the exception
 * - Validation errors -> 400 with structured details
 * - Common MVC errors -> meaningful HTTP status + body
 * - Fallback -> 500
 * Output format is configurable (ApiResponse or ProblemDetail).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private final WebErrorProperties props;

    public GlobalExceptionHandler(WebErrorProperties props) {
        this.props = props;
    }

    /* =========================
     * Business/domain errors
     * ========================= */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> onBusiness(BusinessException ex) {
        int status = ex.httpStatus();
        String traceId = TraceContext.traceId().orElse(null);
        Map<String,Object> details = ex.getMetadata();

        if (props.getFormat() == WebErrorProperties.Format.PROBLEM_DETAIL) {
            ProblemDetail pd = ProblemDetail.forStatus(status);
            pd.setTitle(ex.code());
            if (props.isIncludeMessage()) pd.setDetail(ex.getMessage());
            if (traceId != null) pd.setProperty("traceId", traceId);
            if (details != null && !details.isEmpty()) pd.setProperty("details", details);
            return ResponseEntity.status(status).body(pd);
        } else {
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(ex.code(), ex.getMessage(), details, traceId));
        }
    }

    /* =========================
     * Validation errors (JSR-303)
     * ========================= */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> onMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String,String> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, f -> Optional.ofNullable(f.getDefaultMessage()).orElse("invalid"), (a,b)->a, LinkedHashMap::new));
        return badRequest("validation_failed", "Validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> onConstraintViolation(ConstraintViolationException ex) {
        Map<String,String> details = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(v -> pathOf(v), ConstraintViolation::getMessage, (a,b)->a, LinkedHashMap::new));
        return badRequest("validation_failed", "Validation failed", details);
    }

    private static String pathOf(ConstraintViolation<?> v) {
        return v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
    }

    /* =========================
     * Common MVC errors
     * ========================= */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> onMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("missing_parameter", ex.getParameterName() + " is required", Map.of("parameter", ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        String msg = "Invalid value for parameter '" + field + "'";
        return badRequest("type_mismatch", msg, Map.of("parameter", field, "expectedType", Optional.ofNullable(ex.getRequiredType()).map(Class::getSimpleName).orElse("?")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> onBodyUnreadable(HttpMessageNotReadableException ex) {
        String msg = props.isIncludeMessage() ? "Malformed JSON request: " + ex.getMostSpecificCause().getMessage()
                : "Malformed JSON request";
        return badRequest("malformed_request", msg, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> onMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String msg = "Method " + ex.getMethod() + " not allowed";
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", msg, Map.of("allowed", ex.getSupportedMethods()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> onMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        String msg = "Unsupported media type " + ex.getContentType();
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", msg, Map.of("supported", ex.getSupportedMediaTypes()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> onNoHandler(NoHandlerFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "not_found", "No handler for " + ex.getHttpMethod() + " " + ex.getRequestURL(), Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> onResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String msg = props.isIncludeMessage() ? Objects.toString(ex.getReason(), status.toString()) : status.toString();
        return respond(status, "http_" + status.value(), msg, Map.of());
    }

    /* =========================
     * Fallback
     * ========================= */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> onAny(Exception ex) {
        Map<String,Object> details = new LinkedHashMap<>();
        if (props.isIncludeStackTrace()) {
            details.put("exception", ex.getClass().getName());
            details.put("message", ex.getMessage());
        }
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected error", details);
    }

    /* =========================
     * Helpers
     * ========================= */
    private ResponseEntity<?> badRequest(String code, String message, Map<String,?> details) {
        return respond(HttpStatus.BAD_REQUEST, code, message, details);
    }

    private ResponseEntity<?> respond(HttpStatusCode status, String code, String message, Map<String,?> details) {
        String traceId = TraceContext.traceId().orElse(null);

        if (props.getFormat() == WebErrorProperties.Format.PROBLEM_DETAIL) {
            ProblemDetail pd = ProblemDetail.forStatus(status);
            pd.setTitle(code);
            if (props.isIncludeMessage()) pd.setDetail(message);
            if (traceId != null) pd.setProperty("traceId", traceId);
            if (details != null && !details.isEmpty()) pd.setProperty("details", details);
            return ResponseEntity.status(status).body(pd);
        } else {
            return ResponseEntity.status(status.value())
                    .body(ApiResponse.error(code, props.isIncludeMessage() ? message : null,
                            details == null ? Map.of() : new LinkedHashMap<>(details), traceId));
        }
    }
}

