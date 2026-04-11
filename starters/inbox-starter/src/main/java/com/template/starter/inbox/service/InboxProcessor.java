package com.template.starter.inbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.event.version.EventVersionUtil;

public abstract class InboxProcessor {
    private final ObjectMapper objectMapper;
    private final EventUpcastChain upcastChain;

    /**
     * Backward-compatible constructor for processors that don't need upcasting.
     */
    protected InboxProcessor(ObjectMapper objectMapper) {
        this(objectMapper, EventUpcastChain.empty());
    }

    /**
     * Constructor for processors with version-aware upcasting.
     */
    protected InboxProcessor(ObjectMapper objectMapper, EventUpcastChain upcastChain) {
        this.objectMapper = objectMapper;
        this.upcastChain = upcastChain;
    }

    public abstract void process();

    /**
     * Deserializes the payload without version awareness (backward compatible).
     */
    public <T extends Event> T getType(String payload, Class<T> eventType) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize event payload", e);
        }
    }

    /**
     * Deserializes the payload with version-aware upcasting.
     * If the stored version is older than the current code version,
     * the payload JSON is transformed through the upcaster chain before deserialization.
     *
     * @param payload       the raw JSON payload from the inbox
     * @param eventType     the target event class (current version)
     * @param storedVersion the version the event was serialized with
     * @return the deserialized event, upcasted to the current schema if needed
     */
    public <T extends Event> T getType(String payload, Class<T> eventType, int storedVersion) {
        int currentVersion = EventVersionUtil.getVersion(eventType);
        if (storedVersion < currentVersion) {
            payload = upcastChain.upcast(eventType.getName(), storedVersion, currentVersion, payload);
        }
        return getType(payload, eventType);
    }
}
