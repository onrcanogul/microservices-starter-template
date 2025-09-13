package com.template.microservices.example.event.saga;

import com.template.messaging.saga.SagaStep;
import com.template.messaging.wrapper.EventWrapper;
import com.template.microservices.example.entity.Order;
import com.template.microservices.example.event.OrderCreatedEvent;
import com.template.microservices.example.event.PaymentFailedEvent;
import com.template.starter.inbox.service.InboxService;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class OrderCreatedSaga implements SagaStep<OrderCreatedEvent, PaymentFailedEvent> {
    private final OutboxService outboxService;
    private final InboxService inboxService;

    public OrderCreatedSaga(OutboxService outboxService, InboxService inboxService) {
        this.outboxService = outboxService;
        this.inboxService = inboxService;
    }

    @Override
    public void process(OrderCreatedEvent event) {
        outboxService.save("order.created", event, Order.class, event.orderId().toString());
    }

    @Override
    public void rollback(EventWrapper<PaymentFailedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
