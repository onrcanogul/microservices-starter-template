package com.template.core.tracing;
import org.slf4j.MDC;
import java.util.Optional;

/**
 * Small helper to read trace/span ids from MDC.
 * (observability-starter config adds traceId/spanId to the MDC/log pattern)
 */
public final class TraceContext {
    private TraceContext() {}

    /** Returns current trace id if available (Micrometer/Sleuth-compatible keys). */
    public static Optional<String> traceId() {
        String v = firstNonNull(
                MDC.get("traceId"),
                MDC.get("trace_id"),
                MDC.get("X-B3-TraceId"),
                MDC.get("traceIdString"));
        return Optional.ofNullable(v);
    }

    /** Returns current span id if available. */
    public static Optional<String> spanId() {
        String v = firstNonNull(
                MDC.get("spanId"),
                MDC.get("span_id"),
                MDC.get("X-B3-SpanId"));
        return Optional.ofNullable(v);
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}

