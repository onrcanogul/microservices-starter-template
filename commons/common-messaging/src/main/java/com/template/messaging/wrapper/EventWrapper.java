package com.template.messaging.wrapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventWrapper<T> (
        UUID id,
        String type,
        String source,
        Instant time,
        T event,
        Map<String, String> headers
) {
}
