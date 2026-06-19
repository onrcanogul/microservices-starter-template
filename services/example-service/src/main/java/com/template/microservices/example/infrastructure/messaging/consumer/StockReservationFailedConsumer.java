package com.template.microservices.example.infrastructure.messaging.consumer;

import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.service.InboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockReservationFailedConsumer implements Consumer<StockReservationFailedEvent> {

    private final InboxService inboxService;

    public StockReservationFailedConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "stock.reservation.failed", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<StockReservationFailedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
