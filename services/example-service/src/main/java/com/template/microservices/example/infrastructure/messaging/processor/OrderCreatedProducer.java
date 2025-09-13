package com.template.microservices.example.infrastructure.messaging.processor;

import com.template.messaging.service.producer.Producer;
import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class OrderCreatedProducer implements Producer<OrderCreatedEvent> {
    private final OutboxService outboxService;

    public OrderCreatedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(OrderCreatedEvent event) {
        outboxService.save("order.created", event, Order.class, event.orderId().toString());
    }
}
