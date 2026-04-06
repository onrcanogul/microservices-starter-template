package com.template.starter.inbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class InboxService {
    private static final Logger log = LoggerFactory.getLogger(InboxService.class);
    private final InboxRepository inboxRepository;
    private final ObjectMapper objectMapper;

    public InboxService(InboxRepository inboxRepository, ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(EventWrapper<? extends Event> wrapper) {
        if (inboxRepository.findByIdempotentToken(wrapper.id()).isPresent()) return;
        try {
            inboxRepository.save(Inbox.builder()
                    .type(wrapper.type())
                    .receivedAt(Instant.now())
                    .payload(objectMapper.writeValueAsString(wrapper.event()))
                    .processed(false)
                    .idempotentToken(wrapper.id())
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate inbox entry ignored for token: {}", wrapper.id());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
