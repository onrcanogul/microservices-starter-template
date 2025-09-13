package com.template.microservices.example.event;

import com.template.messaging.event.base.Event;

// move into common
public record OrderCreatedEvent(Long orderId, String sku, Integer amount) implements Event {

}
