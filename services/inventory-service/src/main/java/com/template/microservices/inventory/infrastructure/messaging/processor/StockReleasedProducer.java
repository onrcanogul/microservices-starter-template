package com.template.microservices.inventory.infrastructure.messaging.processor;

import com.template.messaging.event.stock.StockReleasedEvent;
import com.template.messaging.service.producer.Producer;
import com.template.microservices.inventory.domain.entity.Stock;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class StockReleasedProducer implements Producer<StockReleasedEvent> {
    private final OutboxService outboxService;

    public StockReleasedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(StockReleasedEvent event) {
        outboxService.save("stock.released", event, Stock.class, event.orderId().toString());
    }
}
