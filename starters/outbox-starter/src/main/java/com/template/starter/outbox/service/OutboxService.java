package com.template.starter.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
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
        save(destination, event, aggregateType, aggregateId, null);
    }

    public void save(
            String destination,
            Event event,
            Class<?> aggregateType,
            String aggregateId,
            String correlationId
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
                    .correlationId(correlationId)
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize outbox event: " + event.getClass().getSimpleName(), e);
        }
    }
}
