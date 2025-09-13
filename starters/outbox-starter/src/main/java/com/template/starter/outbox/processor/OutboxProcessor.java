package com.template.starter.outbox.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.kafka.publisher.EventPublisher;
import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OutboxProcessor {

    private final OutboxRepository repository;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;

    public OutboxProcessor(OutboxRepository repository, EventPublisher publisher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void processAsync() {
        List<Outbox> outboxes = repository.findByPublishedFalse();
        for (Outbox outbox : outboxes) {
            try {
                Class<?> raw = Class.forName(outbox.getType());
                if (!Event.class.isAssignableFrom(raw)) {
                    throw new IllegalArgumentException("Type is not an Event: " + raw.getName());
                }
                @SuppressWarnings("unchecked")
                Class<? extends Event> clazz = (Class<? extends Event>) raw;
                Event eventObj = objectMapper.readValue(outbox.getPayload(), clazz);
                publisher.publish(outbox.getDestination(), outbox.getType(), eventObj , createHeader(outbox))
                        .thenAccept(sr -> markPublishedNow(outbox.getId()))
                        .exceptionally(ex -> {logFailure(outbox, ex); return null;});
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", outbox.getId());
            }
        }
    }
    private Map<String, String> createHeader(Outbox outbox) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MessageHeaders.TRACE_ID, UUID.randomUUID().toString());
        headers.put(MessageHeaders.KEY, outbox.getType());
        return headers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublishedNow(UUID id) {
        repository.findById(id).ifPresent(o -> {
            o.setPublished(true);
            repository.save(o);
        });
    }

    private void logFailure(Outbox o, Throwable e) {
        log.error("[ERROR] Error while processing outbox: {} - {} - EX: {}", o.getType(), o.getDestination(), e.getMessage());
    }
}

