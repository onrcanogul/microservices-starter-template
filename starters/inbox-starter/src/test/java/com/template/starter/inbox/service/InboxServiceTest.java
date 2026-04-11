package com.template.starter.inbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersionUtil;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private InboxRepository inboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InboxService inboxService;

    record TestEvent(String data) implements Event {}

    @Test
    void extractVersion_nullHeaders_returnsDefaultVersion() throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        EventWrapper<TestEvent> wrapper = new EventWrapper<>(id, "TestEvent", "test", Instant.now(),
                new TestEvent("x"), null);

        when(inboxRepository.findByIdempotentToken(id)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inboxService.save(wrapper);

        ArgumentCaptor<Inbox> captor = ArgumentCaptor.forClass(Inbox.class);
        verify(inboxRepository).save(captor.capture());

        assertThat(captor.getValue().getVersion()).isEqualTo(EventVersionUtil.DEFAULT_VERSION);
    }

    @Test
    void extractVersion_missingVersionKey_returnsDefaultVersion() throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        EventWrapper<TestEvent> wrapper = new EventWrapper<>(id, "TestEvent", "test", Instant.now(),
                new TestEvent("x"), Map.of("other-header", "value"));

        when(inboxRepository.findByIdempotentToken(id)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inboxService.save(wrapper);

        ArgumentCaptor<Inbox> captor = ArgumentCaptor.forClass(Inbox.class);
        verify(inboxRepository).save(captor.capture());

        assertThat(captor.getValue().getVersion()).isEqualTo(EventVersionUtil.DEFAULT_VERSION);
    }

    @Test
    void extractVersion_blankVersionValue_returnsDefaultVersion() throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        EventWrapper<TestEvent> wrapper = new EventWrapper<>(id, "TestEvent", "test", Instant.now(),
                new TestEvent("x"), Map.of(MessageHeaders.EVENT_VERSION, "  "));

        when(inboxRepository.findByIdempotentToken(id)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inboxService.save(wrapper);

        ArgumentCaptor<Inbox> captor = ArgumentCaptor.forClass(Inbox.class);
        verify(inboxRepository).save(captor.capture());

        assertThat(captor.getValue().getVersion()).isEqualTo(EventVersionUtil.DEFAULT_VERSION);
    }

    @Test
    void extractVersion_nonNumericValue_returnsDefaultVersion() throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        EventWrapper<TestEvent> wrapper = new EventWrapper<>(id, "TestEvent", "test", Instant.now(),
                new TestEvent("x"), Map.of(MessageHeaders.EVENT_VERSION, "abc"));

        when(inboxRepository.findByIdempotentToken(id)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inboxService.save(wrapper);

        ArgumentCaptor<Inbox> captor = ArgumentCaptor.forClass(Inbox.class);
        verify(inboxRepository).save(captor.capture());

        assertThat(captor.getValue().getVersion()).isEqualTo(EventVersionUtil.DEFAULT_VERSION);
    }

    @Test
    void extractVersion_validVersionHeader_returnsCorrectVersion() throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        EventWrapper<TestEvent> wrapper = new EventWrapper<>(id, "TestEvent", "test", Instant.now(),
                new TestEvent("x"), Map.of(MessageHeaders.EVENT_VERSION, "3"));

        when(inboxRepository.findByIdempotentToken(id)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inboxService.save(wrapper);

        ArgumentCaptor<Inbox> captor = ArgumentCaptor.forClass(Inbox.class);
        verify(inboxRepository).save(captor.capture());

        assertThat(captor.getValue().getVersion()).isEqualTo(3);
    }
}
