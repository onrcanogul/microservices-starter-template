package com.template.microservices.example.event;

import com.template.messaging.event.base.Event;

public record PaymentFailedEvent (Long orderId) implements Event {
}
