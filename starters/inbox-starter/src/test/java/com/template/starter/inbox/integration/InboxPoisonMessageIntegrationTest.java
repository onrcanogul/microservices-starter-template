package com.template.starter.inbox.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.core.error.StandardErrorCodes;
import com.template.core.exception.BusinessException;
import com.template.starter.inbox.InboxStarterMarker;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.property.InboxProperties;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxProcessingSupport;
import com.template.starter.inbox.service.InboxProcessor;
import com.template.starter.inbox.service.InboxService;
import com.template.test.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (Testcontainers Postgres) coverage of the inbox processing-layer retry / dead-letter:
 * transient failures retry then succeed, a non-retryable BusinessException dies immediately, retries
 * exhaust into dead, and replay resets a dead row. Skipped automatically when Docker is unavailable.
 */
@SpringBootTest(classes = InboxPoisonMessageIntegrationTest.TestApp.class)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class InboxPoisonMessageIntegrationTest extends AbstractPostgresIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = com.template.starter.inbox.InboxAutoConfiguration.class)
    @EntityScan(basePackageClasses = InboxStarterMarker.class)
    @EnableJpaRepositories(basePackageClasses = InboxStarterMarker.class)
    static class TestApp {
        @Bean
        InboxProperties inboxProperties() {
            InboxProperties p = new InboxProperties();
            p.getRetry().setMaxAttempts(3);
            p.getRetry().setBackoff(Duration.ZERO);   // immediate retry eligibility for deterministic tests
            return p;
        }

        @Bean
        InboxProcessingSupport inboxProcessingSupport(InboxRepository repo, TransactionTemplate tx,
                                                      InboxProperties props, ObjectProvider<MeterRegistry> mr) {
            return new InboxProcessingSupport(repo, tx, props, mr);
        }

        @Bean
        ControllableInboxProcessor controllableInboxProcessor(ObjectMapper om, InboxProcessingSupport s) {
            return new ControllableInboxProcessor(om, s);
        }

        @Bean
        InboxService inboxService(InboxRepository repo, ObjectMapper om) {
            return new InboxService(repo, om);
        }
    }

    /** Handler whose failure behaviour is driven by the test. */
    static class ControllableInboxProcessor extends InboxProcessor {
        final AtomicInteger failuresRemaining = new AtomicInteger(0);
        volatile boolean throwBusiness = false;

        ControllableInboxProcessor(ObjectMapper om, InboxProcessingSupport support) {
            super(om, support);
        }

        @Override
        protected void handle(Inbox inbox) {
            if (throwBusiness) {
                throw BusinessException.of(StandardErrorCodes.VALIDATION_FAILED, "poison message");
            }
            if (failuresRemaining.getAndDecrement() > 0) {
                throw new RuntimeException("transient failure");
            }
        }
    }

    @Autowired ControllableInboxProcessor processor;
    @Autowired InboxRepository repository;
    @Autowired InboxService inboxService;

    private UUID insertRow() {
        UUID id = UUID.randomUUID();
        repository.save(Inbox.builder()
                .idempotentToken(id).type("T").payload("{}")
                .processed(false).version(1).receivedAt(Instant.now())
                .build());
        return id;
    }

    private void drain() {
        for (int i = 0; i < 10; i++) {
            processor.process();
        }
    }

    @Test
    void transientFailures_thenSucceeds_marksProcessed() {
        processor.throwBusiness = false;
        processor.failuresRemaining.set(2);
        UUID id = insertRow();

        drain();

        Inbox row = repository.findById(id).orElseThrow();
        assertThat(row.isProcessed()).isTrue();
        assertThat(row.isDead()).isFalse();
        assertThat(row.getAttempts()).isEqualTo(2);
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void businessException_isDeadImmediately_andNotRetried() {
        processor.throwBusiness = true;
        UUID id = insertRow();

        processor.process();
        Inbox afterFirst = repository.findById(id).orElseThrow();
        assertThat(afterFirst.isDead()).isTrue();
        assertThat(afterFirst.isProcessed()).isFalse();
        assertThat(afterFirst.getAttempts()).isEqualTo(1);

        processor.process(); // dead rows are not eligible
        assertThat(repository.findById(id).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void exhaustingRetries_marksDead() {
        processor.throwBusiness = false;
        processor.failuresRemaining.set(100); // always fail
        UUID id = insertRow();

        drain();

        Inbox row = repository.findById(id).orElseThrow();
        assertThat(row.isDead()).isTrue();
        assertThat(row.isProcessed()).isFalse();
        assertThat(row.getAttempts()).isEqualTo(3);
    }

    @Test
    void replay_resetsDeadRow_andReprocesses() {
        processor.throwBusiness = true;
        UUID id = insertRow();
        processor.process();
        assertThat(repository.findById(id).orElseThrow().isDead()).isTrue();

        processor.throwBusiness = false;
        processor.failuresRemaining.set(0);
        inboxService.replay(id);

        drain();

        Inbox row = repository.findById(id).orElseThrow();
        assertThat(row.isProcessed()).isTrue();
        assertThat(row.isDead()).isFalse();
        assertThat(row.getAttempts()).isZero();
    }
}
