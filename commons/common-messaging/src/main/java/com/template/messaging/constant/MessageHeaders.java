package com.template.messaging.constant;

public final class MessageHeaders {
    private MessageHeaders() {}
    public static final String TRACE_ID = "x-trace-id";
    public static final String CORRELATION_ID = "x-correlation-id";
    public static final String CAUSATION_ID = "x-causation-id";
    public static final String KEY = "x-key";
    public static final String EVENT_VERSION = "x-event-version";
    public static final String USER_ID = "x-user-id";
}
