package com.template.microservices.example.infrastructure.messaging.upcaster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.template.messaging.event.version.EventUpcaster;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Transforms OrderCreatedEvent from v1 → v2.
 * V2 added the {@code customerEmail} field; v1 payloads don't have it,
 * so this upcaster inserts a null default.
 */
@Component
@RequiredArgsConstructor
public class OrderCreatedV1ToV2Upcaster implements EventUpcaster {

    private final ObjectMapper objectMapper;

    @Override
    public String eventType() {
        return OrderCreatedEvent.class.getName();
    }

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public int toVersion() {
        return 2;
    }

    @Override
    public String upcast(String jsonPayload) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(jsonPayload);
            if (!node.has("customerEmail")) {
                node.putNull("customerEmail");
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upcast OrderCreatedEvent v1 → v2", e);
        }
    }
}
