package com.template.starter.outbox.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.kafka.publisher.EventPublisher;
import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.property.OutboxProperties;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.util.EventClassResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxProcessorTest {

    private OutboxRepository repository;
    private EventPublisher publisher;
    private ObjectMapper objectMapper;
    private EventClassResolver eventClassResolver;
    private OutboxProperties properties;
    private OutboxProcessor outboxProcessor;

    record TestEvent(String id) implements Event {}

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxRepository.class);
        publisher = mock(EventPublisher.class);
        objectMapper = mock(ObjectMapper.class);
        eventClassResolver = mock(EventClassResolver.class);
        properties = new OutboxProperties();

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TransactionTemplate transactionTemplate = new TransactionTemplate(txm);

        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider = mock(ObjectProvider.class);
        when(meterProvider.getIfAvailable()).thenReturn(null);

        outboxProcessor = new OutboxProcessor(repository, publisher, objectMapper, eventClassResolver,
                properties, transactionTemplate, meterProvider);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubResolveAndDeserialize() throws Exception {
        when(eventClassResolver.resolve(anyString())).thenReturn((Class) TestEvent.class);
        when(objectMapper.readValue(anyString(), eq(TestEvent.class))).thenReturn(new TestEvent("1"));
    }

    @Test
    void process_shouldIncludeVersionHeaderInPublishedMessage() throws Exception {
        Outbox outbox = Outbox.builder()
                .id(UUID.randomUUID()).type(TestEvent.class.getName())
                .payload("{\"id\":\"1\"}").destination("test-topic").version(3).build();

        when(repository.findBatchToPublish(any(), any())).thenReturn(List.of(outbox));
        when(repository.findById(outbox.getId())).thenReturn(Optional.of(outbox));
        stubResolveAndDeserialize();
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxProcessor.process();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("test-topic"), eq(TestEvent.class.getName()), any(Event.class), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsEntry(MessageHeaders.EVENT_VERSION, "3");
        assertThat(outbox.isPublished()).isTrue();
    }

    @Test
    void process_shouldIncludeDefaultVersionWhenNotExplicitlySet() throws Exception {
        Outbox outbox = Outbox.builder()
                .id(UUID.randomUUID()).type(TestEvent.class.getName())
                .payload("{\"id\":\"2\"}").destination("test-topic").build();

        when(repository.findBatchToPublish(any(), any())).thenReturn(List.of(outbox));
        when(repository.findById(outbox.getId())).thenReturn(Optional.of(outbox));
        stubResolveAndDeserialize();
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxProcessor.process();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("test-topic"), eq(TestEvent.class.getName()), any(Event.class), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsEntry(MessageHeaders.EVENT_VERSION, "1");
    }

    @Test
    void process_publishFailure_incrementsAttemptsAndSchedulesRetry() throws Exception {
        Outbox outbox = Outbox.builder()
                .id(UUID.randomUUID()).type(TestEvent.class.getName())
                .payload("{\"id\":\"3\"}").destination("test-topic").build();

        when(repository.findBatchToPublish(any(), any())).thenReturn(List.of(outbox));
        when(repository.findById(outbox.getId())).thenReturn(Optional.of(outbox));
        stubResolveAndDeserialize();
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        outboxProcessor.process();

        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.isDead()).isFalse();
        assertThat(outbox.isPublished()).isFalse();
        assertThat(outbox.getNextAttemptAt()).isNotNull();
        assertThat(outbox.getLastError()).contains("broker down");
    }

    @Test
    void process_publishFailure_atMaxAttempts_marksDead() throws Exception {
        Outbox outbox = Outbox.builder()
                .id(UUID.randomUUID()).type(TestEvent.class.getName())
                .payload("{\"id\":\"4\"}").destination("test-topic")
                .attempts(properties.getRetry().getMaxAttempts() - 1) // next failure crosses the threshold
                .build();

        when(repository.findBatchToPublish(any(), any())).thenReturn(List.of(outbox));
        when(repository.findById(outbox.getId())).thenReturn(Optional.of(outbox));
        stubResolveAndDeserialize();
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("still down")));

        outboxProcessor.process();

        assertThat(outbox.getAttempts()).isEqualTo(properties.getRetry().getMaxAttempts());
        assertThat(outbox.isDead()).isTrue();
        assertThat(outbox.getNextAttemptAt()).isNull();
    }
}
