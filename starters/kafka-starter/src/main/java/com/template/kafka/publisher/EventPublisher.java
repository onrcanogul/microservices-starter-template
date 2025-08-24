package com.template.kafka.publisher;

import com.template.messaging.constant.MessageHeaders;
import com.template.messaging.event.EventWrapper;
import com.template.core.tracing.TraceContext;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes EventEnvelope<T> with auto trace/correlation headers. */
public class EventPublisher {
    private final KafkaTemplate<String, Object> template;
    private final String source;

    public EventPublisher(KafkaTemplate<String, Object> template, String source) {
        this.template = template;
        this.source = source;
    }

    public <T> void publish(String topic, String type, T payload, Map<String,String> headers) {
        Map<String,String> h = new LinkedHashMap<>();
        if (headers != null) h.putAll(headers);
        TraceContext.traceId().ifPresent(t -> h.putIfAbsent(MessageHeaders.TRACE_ID, t));

        String id = h.getOrDefault("id", UUID.randomUUID().toString());
        String key = h.getOrDefault(MessageHeaders.KEY, id);

        EventWrapper<T> env = new EventWrapper<>(id, type, source, Instant.now(), payload, h);
        template.send(topic, key, env);
    }

    public <T> void publish(String topic, String type, T payload) {
        publish(topic, type, payload, Map.of());
    }
}