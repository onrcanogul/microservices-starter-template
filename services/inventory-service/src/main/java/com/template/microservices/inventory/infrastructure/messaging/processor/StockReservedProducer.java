package com.template.microservices.inventory.infrastructure.messaging.processor;

import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.service.producer.Producer;
import com.template.microservices.inventory.domain.entity.Stock;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class StockReservedProducer implements Producer<StockReservedEvent> {
    private final OutboxService outboxService;

    public StockReservedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(StockReservedEvent event) {
        outboxService.save("stock.reserved", event, Stock.class, event.orderId().toString());
    }
}
