package com.template.starter.inbox.service;

import com.template.core.exception.BusinessException;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.property.InboxProperties;
import com.template.starter.inbox.repository.InboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns the poison-message lifecycle for the inbox PROCESSING layer (distinct from the Kafka transport
 * DLT). Polls eligible rows, runs each handler in its OWN transaction, and on failure records
 * attempts / backoff / dead so a single poison message neither blocks siblings nor retries forever.
 *
 * <p>Failure policy (mirrors the transport layer's {@code addNotRetryableExceptions(BusinessException)}):
 * a {@link BusinessException} is non-retryable → dead immediately; any other exception → exponential
 * backoff until {@code maxAttempts}, then dead. Dead rows stay as a queryable DLQ for replay.</p>
 */
public class InboxProcessingSupport {

    private static final Logger log = LoggerFactory.getLogger(InboxProcessingSupport.class);
    private static final int MAX_LAST_ERROR = 4000;

    private final InboxRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final InboxProperties properties;
    private final MeterRegistry meterRegistry;

    public InboxProcessingSupport(InboxRepository repository,
                                  TransactionTemplate transactionTemplate,
                                  InboxProperties properties,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    /** Poll the eligible batch and dispatch each row to {@code handler}, with retry/dead handling. */
    public void process(Consumer<Inbox> handler) {
        List<Inbox> batch = repository.findEligible(Instant.now(), PageRequest.of(0, properties.getBatchSize()));
        for (Inbox inbox : batch) {
            processOne(inbox.getIdempotentToken(), handler);
        }
    }

    private void processOne(UUID id, Consumer<Inbox> handler) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Inbox inbox = repository.findById(id).orElse(null);
                if (inbox == null || inbox.isProcessed() || inbox.isDead()) return;
                handler.accept(inbox);
                inbox.setProcessed(true);
                inbox.setLastError(null);
                repository.save(inbox);
            });
            increment("inbox.processed");
        } catch (BusinessException e) {
            // Non-retryable (business rule violation) — dead immediately, like the transport layer.
            mark(id, e, true);
        } catch (Exception e) {
            mark(id, e, false);
        }
    }

    /** Record a failed attempt in its own transaction; {@code forceDead} for non-retryable errors. */
    private void mark(UUID id, Exception error, boolean forceDead) {
        boolean[] becameDead = {false};
        transactionTemplate.executeWithoutResult(status -> {
            Inbox inbox = repository.findById(id).orElse(null);
            if (inbox == null || inbox.isProcessed() || inbox.isDead()) return;
            int attempts = inbox.getAttempts() + 1;
            inbox.setAttempts(attempts);
            inbox.setLastError(truncate(error.toString()));
            int maxAttempts = properties.getRetry().getMaxAttempts();
            if (forceDead || attempts >= maxAttempts) {
                inbox.setDead(true);
                becameDead[0] = true;
                log.error("Inbox message {} dead-lettered ({}, attempt {}/{}): {}",
                        id, forceDead ? "non-retryable" : "max attempts", attempts, maxAttempts, error.toString());
            } else {
                Instant next = Instant.now().plus(backoffFor(attempts));
                inbox.setNextAttemptAt(next);
                log.warn("Inbox message {} failed (attempt {}/{}), retry after {} at {}: {}",
                        id, attempts, maxAttempts, backoffFor(attempts), next, error.toString());
            }
            repository.save(inbox);
        });
        increment(becameDead[0] ? "inbox.dead" : "inbox.retried");
    }

    private Duration backoffFor(int attempts) {
        long baseMs = properties.getRetry().getBackoff().toMillis();
        long maxMs = properties.getRetry().getMaxBackoff().toMillis();
        int exp = Math.min(Math.max(attempts - 1, 0), 30);
        long ms = baseMs << exp;
        if (ms < 0 || ms > maxMs) ms = maxMs;
        return Duration.ofMillis(ms);
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
