package com.template.starter.inbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.event.version.EventVersionUtil;
import com.template.starter.inbox.entity.Inbox;

/**
 * Base for inbox processors. The base owns polling, per-message transactions, retry and dead-letter
 * (via {@link InboxProcessingSupport}); subclasses only implement {@link #handle(Inbox)} to dispatch a
 * single row to the right branch. They keep using the {@code getType(...)} helpers for deserialization.
 */
public abstract class InboxProcessor {
    private final ObjectMapper objectMapper;
    private final EventUpcastChain upcastChain;
    private final InboxProcessingSupport support;

    /** Backward-compatible constructor for processors that don't need upcasting. */
    protected InboxProcessor(ObjectMapper objectMapper, InboxProcessingSupport support) {
        this(objectMapper, EventUpcastChain.empty(), support);
    }

    /** Constructor for processors with version-aware upcasting. */
    protected InboxProcessor(ObjectMapper objectMapper, EventUpcastChain upcastChain, InboxProcessingSupport support) {
        this.objectMapper = objectMapper;
        this.upcastChain = upcastChain;
        this.support = support;
    }

    /** Template method: poll + per-message retry/dead handling, delegating each row to {@link #handle}. */
    public final void process() {
        support.process(this::handle);
    }

    /**
     * Handle exactly one inbox row. Runs inside its own transaction; throw to trigger retry/dead
     * (throw {@code BusinessException} for a non-retryable poison message). The base marks the row
     * processed and persists it on success.
     */
    protected abstract void handle(Inbox inbox);

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
     */
    public <T extends Event> T getType(String payload, Class<T> eventType, int storedVersion) {
        int currentVersion = EventVersionUtil.getVersion(eventType);
        if (storedVersion < currentVersion) {
            payload = upcastChain.upcast(eventType.getName(), storedVersion, currentVersion, payload);
        }
        return getType(payload, eventType);
    }
}
