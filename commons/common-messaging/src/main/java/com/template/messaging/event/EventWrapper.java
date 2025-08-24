package com.template.messaging.event;

import java.time.Instant;
import java.util.Map;

public record EventWrapper<T> (
        String id,
        String type,
        String source,
        Instant time,
        T event,
        Map<String, String> headers
) {
}
