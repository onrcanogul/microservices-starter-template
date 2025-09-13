package com.template.starter.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void save(
            String destination,
            Event event,
            Class<?> aggregateType,
            String aggregateId
    ){
        try {
            outboxRepository.save(Outbox
                    .builder()
                    .createdAt(Instant.now())
                    .type(event.getClass().getTypeName())
                    .destination(destination)
                    .published(false)
                    .aggregateType(aggregateType.getTypeName())
                    .aggregateId(aggregateId)
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
