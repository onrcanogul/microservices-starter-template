package com.template.starter.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersionUtil;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxService {
    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
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
                    .version(EventVersionUtil.getVersion(event.getClass()))
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize outbox event: " + event.getClass().getSimpleName(), e);
        }
    }

    /**
     * Replay a dead-lettered (or any) outbox row: clear the dead flag, reset attempts and backoff so
     * the publisher re-attempts it on the next cycle.
     */
    @Transactional
    public void replay(UUID id) {
        outboxRepository.findById(id).ifPresent(outbox -> {
            outbox.setDead(false);
            outbox.setPublished(false);
            outbox.setAttempts(0);
            outbox.setNextAttemptAt(null);
            outbox.setLastError(null);
            outboxRepository.save(outbox);
            log.info("Outbox event {} reset for replay", id);
        });
    }
}
