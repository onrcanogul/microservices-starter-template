package com.template.starter.outbox.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.kafka.publisher.EventPublisher;
import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.property.OutboxProperties;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.util.EventClassResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox rows to Kafka. Each row is published OUTSIDE a DB transaction (so a slow broker
 * doesn't hold DB locks); the result (published, or attempts/backoff/dead on failure) is persisted in a
 * short per-row transaction. After {@code maxAttempts} a row is dead-lettered (kept, visible, replayable)
 * instead of being retried forever.
 */
@Slf4j
@Service
public class OutboxProcessor {

    private static final int MAX_LAST_ERROR = 4000;

    private final OutboxRepository repository;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final EventClassResolver eventClassResolver;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxProcessor(OutboxRepository repository,
                           EventPublisher publisher,
                           ObjectMapper objectMapper,
                           EventClassResolver eventClassResolver,
                           OutboxProperties properties,
                           TransactionTemplate transactionTemplate,
                           ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.eventClassResolver = eventClassResolver;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    public void process() {
        List<Outbox> outboxes = repository.findBatchToPublish(
                Instant.now(), PageRequest.of(0, properties.getBatchSize()));
        for (Outbox outbox : outboxes) {
            publishOne(outbox);
        }
    }

    private void publishOne(Outbox outbox) {
        // At-least-once: a publish that times out AFTER the broker accepted it is retried, so the same
        // event may be re-published. Consumers dedup via the inbox idempotent token; the producer also
        // runs with enable.idempotence=true. The publish runs outside any DB transaction on purpose.
        UUID id = outbox.getId();
        try {
            Class<? extends Event> clazz = eventClassResolver.resolve(outbox.getType());
            Event eventObj = objectMapper.readValue(outbox.getPayload(), clazz);
            publisher.publish(outbox.getDestination(), outbox.getType(), eventObj, createHeader(outbox))
                    .get(properties.getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
            transactionTemplate.executeWithoutResult(status -> markPublished(id));
            increment("outbox.published");
        } catch (Exception e) {
            recordFailure(id, e);
        }
    }

    private void markPublished(UUID id) {
        repository.findById(id).ifPresent(outbox -> {
            if (outbox.isPublished() || outbox.isDead()) return; // defensive: don't resurrect a dead row
            outbox.setPublished(true);
            outbox.setLastError(null);
            repository.save(outbox);
        });
    }

    private void recordFailure(UUID id, Exception error) {
        boolean[] becameDead = {false};
        transactionTemplate.executeWithoutResult(status -> {
            Outbox outbox = repository.findById(id).orElse(null);
            if (outbox == null || outbox.isPublished() || outbox.isDead()) return;
            int attempts = outbox.getAttempts() + 1;
            outbox.setAttempts(attempts);
            outbox.setLastError(truncate(error.toString()));
            int maxAttempts = properties.getRetry().getMaxAttempts();
            if (attempts >= maxAttempts) {
                outbox.setDead(true);
                becameDead[0] = true;
                log.error("Outbox event {} dead-lettered after {} attempts: {}", id, attempts, error.toString());
            } else {
                Instant next = Instant.now().plus(backoffFor(attempts));
                outbox.setNextAttemptAt(next);
                log.warn("Outbox publish failed for {} (attempt {}/{}), retry after {} at {}: {}",
                        id, attempts, maxAttempts, backoffFor(attempts), next, error.toString());
            }
            repository.save(outbox);
        });
        increment(becameDead[0] ? "outbox.dead" : "outbox.retried");
    }

    private Duration backoffFor(int attempts) {
        long baseMs = properties.getRetry().getBackoff().toMillis();
        long maxMs = properties.getRetry().getMaxBackoff().toMillis();
        int exp = Math.min(Math.max(attempts - 1, 0), 30);
        long ms = baseMs << exp;
        if (ms < 0 || ms > maxMs) ms = maxMs;
        return Duration.ofMillis(ms);
    }

    private Map<String, String> createHeader(Outbox outbox) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MessageHeaders.TRACE_ID, UUID.randomUUID().toString());
        headers.put(MessageHeaders.KEY, outbox.getType());
        if (outbox.getCorrelationId() != null) {
            headers.put(MessageHeaders.CORRELATION_ID, outbox.getCorrelationId());
        }
        headers.put(MessageHeaders.EVENT_VERSION, String.valueOf(outbox.getVersion()));
        return headers;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_LAST_ERROR ? s : s.substring(0, MAX_LAST_ERROR);
    }

    private void increment(String metric) {
        if (meterRegistry != null) {
            Counter.builder(metric).register(meterRegistry).increment();
        }
    }
}
