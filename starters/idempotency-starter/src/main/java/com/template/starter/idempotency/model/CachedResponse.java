package com.template.starter.idempotency.model;

import java.util.Map;

/**
 * Immutable representation of a cached HTTP response for idempotent replay.
 * Stored as JSON in Redis. Includes response headers so that {@code Location},
 * {@code ETag}, and custom headers are faithfully replayed.
 */
public record CachedResponse(
        int status,
        String contentType,
        String body,
        Map<String, String> headers
) {}
