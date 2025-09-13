package com.template.microservices.example.infrastructure.messaging.consumer;

import com.template.messaging.saga.SagaRollback;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.infrastructure.messaging.PaymentFailedEvent;
import com.template.microservices.example.infrastructure.messaging.processor.OrderCreatedProducer;
import com.template.starter.inbox.service.InboxService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SagaRollback(source = OrderCreatedEvent.class, sourcesProcessor = OrderCreatedProducer.class)
public class PaymentFailedConsumer implements Consumer<PaymentFailedEvent> {
    private final InboxService inboxService;

    public PaymentFailedConsumer(InboxService inboxService, OrderService orderService) {
        this.inboxService = inboxService;
    }

    @Override
    @Transactional
    @KafkaListener(topics = "payment.failed", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<PaymentFailedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
