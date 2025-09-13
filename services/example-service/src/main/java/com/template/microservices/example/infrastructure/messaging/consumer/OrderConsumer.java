package com.template.microservices.example.infrastructure.messaging.consumer;

import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.starter.inbox.service.InboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderConsumer implements Consumer<OrderCreatedEvent> {

    private final InboxService inboxService;

    public OrderConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "orders.created", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<OrderCreatedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
