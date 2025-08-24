package com.template.microservices.example.event.processor;

import com.template.messaging.saga.SagaStep;
import com.template.microservices.example.event.OrderCreatedEvent;
import com.template.microservices.example.event.PaymentFailedEvent;

public class OrderCreatedProcessor implements SagaStep<OrderCreatedEvent, PaymentFailedEvent> {
    @Override
    public void process(OrderCreatedEvent event) {
        /* process to save outbox */
    }

    @Override
    public void rollback(PaymentFailedEvent payload) {
        /* process to rollback */
    }
}
