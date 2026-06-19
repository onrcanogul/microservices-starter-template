package com.template.microservices.example.infrastructure.messaging.processor;

import com.template.messaging.event.stock.StockReleaseRequestedEvent;
import com.template.messaging.service.producer.Producer;
import com.template.microservices.example.domain.entity.Order;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class StockReleaseRequestedProducer implements Producer<StockReleaseRequestedEvent> {
    private final OutboxService outboxService;

    public StockReleaseRequestedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(StockReleaseRequestedEvent event) {
        outboxService.save("stock.release.requested", event, Order.class, event.orderId().toString());
    }
}
