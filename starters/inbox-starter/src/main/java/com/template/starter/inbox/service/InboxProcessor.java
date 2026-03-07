package com.template.starter.inbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;

public abstract class InboxProcessor {
    private final ObjectMapper objectMapper;

    protected InboxProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public abstract void process();

    public <T extends Event> T getType(String payload, Class<T> eventType) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize event payload", e);
        }
    }
}
