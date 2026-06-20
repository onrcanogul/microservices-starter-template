package com.template.starter.outbox.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.kafka.publisher.EventPublisher;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.OutboxStarterMarker;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.processor.OutboxProcessor;
import com.template.starter.outbox.property.OutboxProperties;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.service.OutboxService;
import com.template.starter.outbox.util.EventClassResolver;
import com.template.test.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Real-DB (Testcontainers Postgres) coverage of the outbox publish-layer retry / dead-letter:
 * publish failures retry then dead-letter at maxAttempts; a successful publish marks the row published;
 * replay resets a dead row. The Kafka publisher is mocked (no broker). Skipped without Docker.
 */
@SpringBootTest(classes = OutboxPublishRetryIntegrationTest.TestApp.class)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class OutboxPublishRetryIntegrationTest extends AbstractPostgresIntegrationTest {

    public record TestEvent(String id) implements Event {}

    @Configuration
    @EnableAutoConfiguration(exclude = {
            com.template.starter.outbox.OutboxStarterAutoConfiguration.class,
            com.template.kafka.KafkaMessagingAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = OutboxStarterMarker.class)
    @EnableJpaRepositories(basePackageClasses = OutboxStarterMarker.class)
    static class TestApp {
        @Bean
        OutboxProperties outboxProperties() {
            OutboxProperties p = new OutboxProperties();
            p.getRetry().setMaxAttempts(3);
            p.getRetry().setBackoff(Duration.ZERO);
            return p;
        }

        @Bean
        EventClassResolver eventClassResolver() {
            return new EventClassResolver();
        }

        @Bean
        EventPublisher eventPublisher() {
            return Mockito.mock(EventPublisher.class);
        }

        @Bean
        OutboxProcessor outboxProcessor(OutboxRepository repo, EventPublisher publisher, ObjectMapper om,
                                        EventClassResolver resolver, OutboxProperties props,
                                        TransactionTemplate tx, ObjectProvider<MeterRegistry> mr) {
            return new OutboxProcessor(repo, publisher, om, resolver, props, tx, mr);
        }

        @Bean
        OutboxService outboxService(OutboxRepository repo, ObjectMapper om) {
            return new OutboxService(repo, om);
        }
    }

    @Autowired OutboxProcessor processor;
    @Autowired OutboxRepository repository;
    @Autowired OutboxService outboxService;
    @Autowired EventPublisher publisher;

    @BeforeEach
    void resetPublisher() {
        Mockito.reset(publisher);
    }

    private UUID insertRow() {
        Outbox outbox = Outbox.builder()
                .type(TestEvent.class.getName())
                .payload("{\"id\":\"1\"}")
                .destination("test-topic")
                .published(false)
                .version(1)
                .createdAt(java.time.Instant.now())
                .build();
        return repository.save(outbox).getId();
    }

    private void publishFails() {
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    }

    private void publishSucceeds() {
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void drain() {
        for (int i = 0; i < 10; i++) {
            processor.process();
        }
    }

    @Test
    void publishFailures_retryThenDeadLetter() {
        publishFails();
        UUID id = insertRow();

        drain();

        Outbox row = repository.findById(id).orElseThrow();
        assertThat(row.isDead()).isTrue();
        assertThat(row.isPublished()).isFalse();
        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getLastError()).contains("broker down");
    }

    @Test
    void publishSuccess_marksPublished() {
        publishSucceeds();
        UUID id = insertRow();

        processor.process();

        Outbox row = repository.findById(id).orElseThrow();
        assertThat(row.isPublished()).isTrue();
        assertThat(row.isDead()).isFalse();
    }

    @Test
    void replay_resetsDeadRow_andPublishes() {
        publishFails();
        UUID id = insertRow();
        drain();
        assertThat(repository.findById(id).orElseThrow().isDead()).isTrue();

        publishSucceeds();
        outboxService.replay(id);
        drain();

        Outbox row = repository.findById(id).orElseThrow();
        assertThat(row.isPublished()).isTrue();
        assertThat(row.isDead()).isFalse();
        assertThat(row.getAttempts()).isZero();
    }
}
