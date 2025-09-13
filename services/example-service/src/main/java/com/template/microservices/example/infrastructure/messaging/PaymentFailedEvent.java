package com.template.microservices.example.infrastructure.messaging;

import com.template.messaging.event.base.Event;

public record PaymentFailedEvent (Long orderId) implements Event {
}
