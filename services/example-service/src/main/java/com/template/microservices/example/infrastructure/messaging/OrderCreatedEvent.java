package com.template.microservices.example.infrastructure.messaging;

import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersion;

@EventVersion(2)
public record OrderCreatedEvent(Long orderId, String sku, Integer amount, String customerEmail) implements Event {
}
