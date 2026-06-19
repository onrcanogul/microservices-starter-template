---
name: error-handling
description: "Use when adding error handling, creating error codes, throwing business exceptions, or customizing error response format. Covers the ErrorCode→BusinessException→GlobalExceptionHandler pipeline, dual-format output (ApiResponse vs ProblemDetail), trace correlation, and service-specific error code creation."
---

# Error Handling

This project uses a structured three-layer error pipeline. Understanding the flow prevents common mistakes like catching exceptions in controllers or returning inconsistent error shapes.

## The Pipeline

```
ErrorCode (interface)  →  BusinessException (thrown in service layer)  →  GlobalExceptionHandler (maps to HTTP response)
```

Every domain error flows through this pipeline. The handler produces either `ApiResponse` or `ProblemDetail` (RFC 9457) based on configuration.

## Creating Service-Specific Error Codes

Each service defines its own error codes as an enum implementing `ErrorCode`:

```java
public enum OrderErrorCodes implements ErrorCode {
    ORDER_NOT_FOUND("order_not_found", 404),
    INSUFFICIENT_STOCK("insufficient_stock", 409),
    ORDER_ALREADY_CANCELLED("order_already_cancelled", 422);

    private final String code;
    private final int http;

    OrderErrorCodes(String code, int http) { this.code = code; this.http = http; }
    @Override public String code() { return code; }
    @Override public int httpStatus() { return http; }
}
```

Reuse `StandardErrorCodes` for generic cases (NOT_FOUND, VALIDATION_FAILED, CONFLICT, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, INTERNAL_ERROR, SERVICE_UNAVAILABLE, TIMEOUT). Create service-specific codes only for domain-specific errors.

`ErrorCode` also provides `messageKey()` (defaults to `"error." + code()`) for i18n key resolution.

## Throwing BusinessException

Use the factory helpers — they're cleaner than constructors:

```java
// Simple
throw BusinessException.of(StandardErrorCodes.NOT_FOUND, "Order not found with id: " + id);

// With structured metadata (appears in error response details)
throw BusinessException.of(OrderErrorCodes.INSUFFICIENT_STOCK, "Not enough stock",
    Map.of("requested", requestedQty, "available", availableQty));

// Wrapping a caught exception (preserves cause chain for logging)
throw new BusinessException(OrderErrorCodes.PAYMENT_FAILED, "Payment failed", cause);
```

The metadata map is stored as an unmodifiable `LinkedHashMap` copy. It appears in the `details` field of the error response, so include any information the client needs to handle the error programmatically.

## Where to Throw

Throw `BusinessException` in the **service layer**, never in controllers. Controllers should be thin delegates:

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<OrderDto>> getById(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
}
```

## GlobalExceptionHandler Behavior

The handler (registered via `CommonWebAutoConfiguration`) maps exceptions to responses:

| Exception | Code | HTTP Status |
|-----------|------|-------------|
| `BusinessException` | from ErrorCode | from ErrorCode |
| `MethodArgumentNotValidException` | `validation_failed` | 400 |
| `ConstraintViolationException` | `validation_failed` | 400 |
| `MissingServletRequestParameterException` | `missing_parameter` | 400 |
| `MethodArgumentTypeMismatchException` | `type_mismatch` | 400 |
| `HttpMessageNotReadableException` | `malformed_request` | 400 |
| `HttpRequestMethodNotSupportedException` | `method_not_allowed` | 405 |
| `HttpMediaTypeNotSupportedException` | `unsupported_media_type` | 415 |
| `NoHandlerFoundException` | `not_found` | 404 |
| `ResponseStatusException` | `http_<status>` | from exception |
| Any other `Exception` | `internal_error` | 500 |

Validation errors automatically extract field-level details from binding results.

## Response Format Configuration

Controlled via `acme.web.error.*` properties:

```yaml
acme:
  web:
    error:
      format: API_RESPONSE     # or PROBLEM_DETAIL
      include-message: true     # include exception message in error responses
      include-stack-trace: false # include exception class + message in 5xx details (dev only)
```

**API_RESPONSE format** (default):
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "order_not_found",
    "message": "Order not found with id: 42",
    "details": { "orderId": 42 },
    "traceId": "abc123"
  }
}
```

**PROBLEM_DETAIL format** (RFC 9457):
```json
{
  "type": "about:blank",
  "title": "order_not_found",
  "status": 404,
  "detail": "Order not found with id: 42",
  "traceId": "abc123",
  "details": { "orderId": 42 }
}
```

## Trace Correlation

`TraceContext.traceId()` auto-reads the trace ID from MDC (checks keys: `traceId`, `trace_id`, `X-B3-TraceId`, `traceIdString`). The `GlobalExceptionHandler` attaches it to every error response. This allows clients to send the trace ID to support for log correlation.

## Success Responses

Wrap in `ApiResponse.ok(data)`. For pagination, use `PageResponse.of(content, page, size, totalElements)` — it computes `totalPages` and `hasNext`. For 201, use `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created))`.

## Gotchas

- The handler is registered with `@Order(Ordered.HIGHEST_PRECEDENCE)` — it takes priority over any other advice.
- `@ConditionalOnMissingBean` on the handler bean means you can override it entirely by defining your own `GlobalExceptionHandler` bean.
- `include-message: false` suppresses messages for framework exceptions (validation, type mismatch, etc.) in both formats. For `BusinessException`, it only suppresses in `PROBLEM_DETAIL` format — `API_RESPONSE` always includes the business exception message.
- `include-stack-trace: true` adds exception class name and message to 5xx response details (no actual stack trace). Never set in production.
- BusinessException metadata values should be serializable (primitives, strings, collections).
