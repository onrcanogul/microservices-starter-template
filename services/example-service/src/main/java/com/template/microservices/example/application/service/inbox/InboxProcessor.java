package com.template.microservices.example.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.infrastructure.messaging.PaymentFailedEvent;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class InboxProcessor {
    private final InboxRepository inboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public InboxProcessor(InboxRepository inboxRepository, ObjectMapper objectMapper, OrderService orderService) {
        this.inboxRepository = inboxRepository;
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    public void process() {
        List<Inbox> inboxes = inboxRepository.findByProcessedFalse();
        for (Inbox inbox: inboxes) {
            String type = inbox.getType();
            if(Objects.equals(type, OrderCreatedEvent.class.getName())) {
                OrderCreatedEvent event = getType(inbox.getPayload(), OrderCreatedEvent.class);
                //do something with order service
            }
            else if (Objects.equals(type, PaymentFailedEvent.class.getName())) {
                PaymentFailedEvent event = getType(inbox.getPayload(), PaymentFailedEvent.class);
                orderService.delete(event.orderId());
            }
        }
    }

    private <T extends Event> T getType(String payload, Class<T> eventType) {
        return objectMapper.convertValue(payload, eventType);
    }
}
