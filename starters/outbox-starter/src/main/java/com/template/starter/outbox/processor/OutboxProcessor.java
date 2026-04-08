package com.template.starter.outbox.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.kafka.publisher.EventPublisher;
import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.util.EventClassResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OutboxProcessor {

    private final OutboxRepository repository;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final EventClassResolver eventClassResolver;

    public OutboxProcessor(OutboxRepository repository, EventPublisher publisher,
                           ObjectMapper objectMapper, EventClassResolver eventClassResolver) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.eventClassResolver = eventClassResolver;
    }

    @Transactional
    public void process() {
        List<Outbox> outboxes = repository.findTop100ByPublishedFalse();
        for (Outbox outbox : outboxes) {
            try {
                Class<? extends Event> clazz = eventClassResolver.resolve(outbox.getType());
                Event eventObj = objectMapper.readValue(outbox.getPayload(), clazz);
                publisher.publish(outbox.getDestination(), outbox.getType(), eventObj, createHeader(outbox))
                        .get(5, TimeUnit.SECONDS);
                outbox.setPublished(true);
                repository.save(outbox);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", outbox.getId(), e);
            }
        }
    }

    private Map<String, String> createHeader(Outbox outbox) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MessageHeaders.TRACE_ID, UUID.randomUUID().toString());
        headers.put(MessageHeaders.KEY, outbox.getType());
        if (outbox.getCorrelationId() != null) {
            headers.put(MessageHeaders.CORRELATION_ID, outbox.getCorrelationId());
        }
        return headers;
    }
}

