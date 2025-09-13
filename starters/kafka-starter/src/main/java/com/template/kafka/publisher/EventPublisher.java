package com.template.kafka.publisher;

import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.base.Event;
import com.template.messaging.wrapper.EventWrapper;
import com.template.core.tracing.TraceContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Publishes EventEnvelope<T> with auto trace/correlation headers. */
public class EventPublisher {
    private final KafkaTemplate<String, Object> template;
    private final String source;

    public EventPublisher(KafkaTemplate<String, Object> template, String source) {
        this.template = template;
        this.source = source;
    }

    public <T extends Event> CompletableFuture<SendResult<String, Object>> publish(String topic, String type, T payload, Map<String,String> headers) {
        Map<String, String> h = new LinkedHashMap<>();
        if (headers != null) h.putAll(headers);
        TraceContext.traceId().ifPresent(t -> h.putIfAbsent(MessageHeaders.TRACE_ID, t));

        UUID id = UUID.fromString(h.getOrDefault("id", UUID.randomUUID().toString()));
        String key = h.getOrDefault(MessageHeaders.KEY, id.toString());

        EventWrapper<T> env = new EventWrapper<>(id, type, source, Instant.now(), payload, h);
        return template.send(topic, key, env);
    }

    public <T extends Event> void publish(String topic, String type, T payload) {
        publish(topic, type, payload, Map.of());
    }
}