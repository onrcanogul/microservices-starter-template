package com.template.microservices.example.infrastructure.messaging.consumer;

import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.service.InboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockReservedConsumer implements Consumer<StockReservedEvent> {

    private final InboxService inboxService;

    public StockReservedConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "stock.reserved", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<StockReservedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
