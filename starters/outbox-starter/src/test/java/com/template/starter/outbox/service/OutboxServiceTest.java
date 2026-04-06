package com.template.starter.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    record TestEvent(String id) implements Event {}

    @Test
    void save_shouldPersistOutboxEntry() throws JsonProcessingException {
        TestEvent event = new TestEvent("123");
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"id\":\"123\"}");

        outboxService.save("test.topic", event, String.class, "123");

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());

        Outbox saved = captor.getValue();
        assertThat(saved.getDestination()).isEqualTo("test.topic");
        assertThat(saved.getPayload()).isEqualTo("{\"id\":\"123\"}");
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.getAggregateId()).isEqualTo("123");
    }

    @Test
    void save_shouldThrowUncheckedIOException_whenSerializationFails() throws JsonProcessingException {
        TestEvent event = new TestEvent("fail");
        when(objectMapper.writeValueAsString(event))
                .thenThrow(new JsonProcessingException("bad") {});

        assertThatThrownBy(() -> outboxService.save("test.topic", event, String.class, "fail"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to serialize outbox event");
    }
}
