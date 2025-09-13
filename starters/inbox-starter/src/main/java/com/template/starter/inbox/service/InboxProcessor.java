package com.template.starter.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class InboxProcessor {
    private ObjectMapper objectMapper;
    protected InboxProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public abstract void process();

    public  <T extends Event> T getType(String payload, Class<T> eventType) {
        return objectMapper.convertValue(payload, eventType);
    }

}
