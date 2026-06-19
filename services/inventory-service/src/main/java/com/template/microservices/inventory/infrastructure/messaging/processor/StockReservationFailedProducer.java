package com.template.microservices.inventory.infrastructure.messaging.processor;

import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.service.producer.Producer;
import com.template.microservices.inventory.domain.entity.Stock;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class StockReservationFailedProducer implements Producer<StockReservationFailedEvent> {
    private final OutboxService outboxService;

    public StockReservationFailedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(StockReservationFailedEvent event) {
        outboxService.save("stock.reservation.failed", event, Stock.class, event.orderId().toString());
    }
}
