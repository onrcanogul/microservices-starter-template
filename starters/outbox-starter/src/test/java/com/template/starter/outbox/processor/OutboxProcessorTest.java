package com.template.starter.outbox.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.kafka.publisher.EventPublisher;
import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.util.EventClassResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private EventPublisher publisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EventClassResolver eventClassResolver;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    record TestEvent(String id) implements Event {}

    @Test
    void process_shouldIncludeVersionHeaderInPublishedMessage() throws Exception {
        Outbox outbox = Outbox.builder()
                .id(UUID.randomUUID())
                .type(TestEvent.class.getName())
                .payload("{\"id\":\"1\"}")
                .destination("test-topic")
                .version(3)
                .build();

        when(repository.findTop100ByPublishedFalse()).thenReturn(List.of(outbox));
        when(eventClassResolver.resolve(anyString())).thenReturn((Class) TestEvent.class);
        when(objectMapper.readValue(anyString(), eq(TestEvent.class))).thenReturn(new TestEvent("1"));
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxProcessor.process();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("test-topic"), eq(TestEvent.class.getName()), any(Event.class), headersCaptor.capture());

        Map<String, String> headers = headersCaptor.getValue();
        assertThat(headers).containsEntry(MessageHeaders.EVENT_VERSION, "3");
    }

    @Test
    void process_shouldIncludeDefaultVersionWhenNotExplicitlySet() throws Exception {
        Outbox outbox = Outbox.builder()
                .id(UUID.randomUUID())
                .type(TestEvent.class.getName())
                .payload("{\"id\":\"2\"}")
                .destination("test-topic")
                .build();

        when(repository.findTop100ByPublishedFalse()).thenReturn(List.of(outbox));
        when(eventClassResolver.resolve(anyString())).thenReturn((Class) TestEvent.class);
        when(objectMapper.readValue(anyString(), eq(TestEvent.class))).thenReturn(new TestEvent("2"));
        when(publisher.publish(anyString(), anyString(), any(Event.class), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxProcessor.process();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("test-topic"), eq(TestEvent.class.getName()), any(Event.class), headersCaptor.capture());

        Map<String, String> headers = headersCaptor.getValue();
        assertThat(headers).containsEntry(MessageHeaders.EVENT_VERSION, "1");
    }
}
