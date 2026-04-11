package com.template.kafka.publisher;

import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.messaging.wrapper.EventWrapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class EventPublisherTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private EventPublisher publisher;

    record TestEvent(String data) implements Event {}

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new EventPublisher(kafkaTemplate, "test-service");

        SendResult<String, Object> sendResult = new SendResult<>(
                new ProducerRecord<>("topic", "key", "value"),
                new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0)
        );
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publish_propagatesMdcCorrelationIdToHeaders() {
        MDC.put("correlationId", "corr-123");

        publisher.publish("topic", "TestEvent", new TestEvent("test"));

        ArgumentCaptor<EventWrapper> captor = ArgumentCaptor.forClass(EventWrapper.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        EventWrapper<?> wrapper = captor.getValue();
        assertThat(wrapper.headers()).containsEntry(MessageHeaders.CORRELATION_ID, "corr-123");
    }

    @Test
    void publish_propagatesMdcUserIdToHeaders() {
        MDC.put("userId", "user-42");

        publisher.publish("topic", "TestEvent", new TestEvent("test"));

        ArgumentCaptor<EventWrapper> captor = ArgumentCaptor.forClass(EventWrapper.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        EventWrapper<?> wrapper = captor.getValue();
        assertThat(wrapper.headers()).containsEntry(MessageHeaders.USER_ID, "user-42");
    }

    @Test
    void publish_explicitHeadersTakePrecedenceOverMdc() {
        MDC.put("correlationId", "mdc-corr");
        MDC.put("userId", "mdc-user");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MessageHeaders.CORRELATION_ID, "explicit-corr");
        headers.put(MessageHeaders.USER_ID, "explicit-user");

        publisher.publish("topic", "TestEvent", new TestEvent("test"), headers);

        ArgumentCaptor<EventWrapper> captor = ArgumentCaptor.forClass(EventWrapper.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        EventWrapper<?> wrapper = captor.getValue();
        assertThat(wrapper.headers()).containsEntry(MessageHeaders.CORRELATION_ID, "explicit-corr");
        assertThat(wrapper.headers()).containsEntry(MessageHeaders.USER_ID, "explicit-user");
    }

    @Test
    void publish_emptyMdcDoesNotAddHeaders() {
        publisher.publish("topic", "TestEvent", new TestEvent("test"));

        ArgumentCaptor<EventWrapper> captor = ArgumentCaptor.forClass(EventWrapper.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        EventWrapper<?> wrapper = captor.getValue();
        assertThat(wrapper.headers()).doesNotContainKey(MessageHeaders.USER_ID);
    }

    @Test
    void publish_blankMdcValuesNotPropagated() {
        MDC.put("correlationId", "  ");
        MDC.put("userId", "");

        publisher.publish("topic", "TestEvent", new TestEvent("test"));

        ArgumentCaptor<EventWrapper> captor = ArgumentCaptor.forClass(EventWrapper.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        EventWrapper<?> wrapper = captor.getValue();
        assertThat(wrapper.headers()).doesNotContainKey(MessageHeaders.USER_ID);
    }
}
