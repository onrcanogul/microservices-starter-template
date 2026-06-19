package com.template.microservices.example.infrastructure.messaging.consumer;

import com.template.messaging.event.stock.StockReleasedEvent;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.service.InboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockReleasedConsumer implements Consumer<StockReleasedEvent> {

    private final InboxService inboxService;

    public StockReleasedConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "stock.released", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<StockReleasedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
