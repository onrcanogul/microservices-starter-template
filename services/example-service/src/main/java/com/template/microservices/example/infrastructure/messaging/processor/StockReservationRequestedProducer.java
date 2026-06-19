package com.template.microservices.example.infrastructure.messaging.processor;

import com.template.messaging.event.stock.StockReservationRequestedEvent;
import com.template.messaging.service.producer.Producer;
import com.template.microservices.example.domain.entity.Order;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class StockReservationRequestedProducer implements Producer<StockReservationRequestedEvent> {
    private final OutboxService outboxService;

    public StockReservationRequestedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(StockReservationRequestedEvent event) {
        outboxService.save("stock.reservation.requested", event, Order.class, event.orderId().toString());
    }
}
