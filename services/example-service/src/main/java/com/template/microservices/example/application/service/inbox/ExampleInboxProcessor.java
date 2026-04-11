package com.template.microservices.example.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.infrastructure.messaging.PaymentFailedEvent;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ExampleInboxProcessor extends InboxProcessor {
    private final InboxRepository inboxRepository;
    private final OrderService orderService;

    public ExampleInboxProcessor(
            InboxRepository inboxRepository,
            ObjectMapper objectMapper,
            OrderService orderService,
            EventUpcastChain upcastChain
    ) {
        super(objectMapper, upcastChain);
        this.inboxRepository = inboxRepository;
        this.orderService = orderService;
    }

    @Transactional
    public void process() {
        List<Inbox> inboxes = inboxRepository.findByProcessedFalse();
        for (Inbox inbox: inboxes) {
            String type = inbox.getType();
            int version = inbox.getVersion();

            if(Objects.equals(type, OrderCreatedEvent.class.getName())) {
                OrderCreatedEvent event = getType(inbox.getPayload(), OrderCreatedEvent.class, version);
                //do something with order service
            }
            else if (Objects.equals(type, PaymentFailedEvent.class.getName())) {
                PaymentFailedEvent event = getType(inbox.getPayload(), PaymentFailedEvent.class, version);
                orderService.delete(event.orderId());
            }
            inbox.setProcessed(true);
            inboxRepository.save(inbox);
        }
    }
}
