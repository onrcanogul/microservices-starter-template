package com.template.starter.inbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InboxService {
    private final InboxRepository inboxRepository;
    private final ObjectMapper objectMapper;

    public InboxService(InboxRepository inboxRepository, ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.objectMapper = objectMapper;
    }

    public void save(EventWrapper<? extends Event> wrapper) {
        if (inboxRepository.findByIdempotentToken(wrapper.id()).isPresent()) return;
        try {
            inboxRepository.save(Inbox.builder()
                    .type(wrapper.type())
                    .receivedAt(LocalDateTime.now())
                    .payload(objectMapper.writeValueAsString(wrapper.event()))
                    .processed(false)
                    .idempotentToken(wrapper.id())
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
