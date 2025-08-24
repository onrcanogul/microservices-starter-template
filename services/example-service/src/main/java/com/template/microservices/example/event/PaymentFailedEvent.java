package com.template.microservices.example.event;

public record PaymentFailedEvent (Long orderId) {
}
