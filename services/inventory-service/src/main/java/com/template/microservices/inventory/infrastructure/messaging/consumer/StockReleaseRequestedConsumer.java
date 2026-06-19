package com.template.microservices.inventory.infrastructure.messaging.consumer;

import com.template.messaging.event.stock.StockReleaseRequestedEvent;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.starter.inbox.service.InboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockReleaseRequestedConsumer implements Consumer<StockReleaseRequestedEvent> {

    private final InboxService inboxService;

    public StockReleaseRequestedConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "stock.release.requested", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<StockReleaseRequestedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
