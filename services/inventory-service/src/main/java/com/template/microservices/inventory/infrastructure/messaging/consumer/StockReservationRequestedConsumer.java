package com.template.microservices.inventory.infrastructure.messaging.consumer;

import com.template.messaging.event.stock.StockReservationRequestedEvent;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.service.InboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockReservationRequestedConsumer implements Consumer<StockReservationRequestedEvent> {

    private final InboxService inboxService;

    public StockReservationRequestedConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "stock.reservation.requested", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<StockReservationRequestedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
