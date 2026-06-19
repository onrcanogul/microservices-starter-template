package com.template.microservices.example.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.stock.StockReleasedEvent;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.infrastructure.messaging.PaymentFailedEvent;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
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

            if (Objects.equals(type, OrderCreatedEvent.class.getName())) {
                OrderCreatedEvent event = getType(inbox.getPayload(), OrderCreatedEvent.class, version);
                // Order is created here (PENDING) and stock reservation is requested in the same TX.
                orderService.createOrder(event.sku(), event.amount());
            }
            else if (Objects.equals(type, StockReservedEvent.class.getName())) {
                StockReservedEvent event = getType(inbox.getPayload(), StockReservedEvent.class, version);
                orderService.markConfirmed(event.orderId());
            }
            else if (Objects.equals(type, StockReservationFailedEvent.class.getName())) {
                StockReservationFailedEvent event = getType(inbox.getPayload(), StockReservationFailedEvent.class, version);
                log.warn("Stock reservation failed for orderId={}: {}", event.orderId(), event.reason());
                orderService.markRejected(event.orderId());
            }
            else if (Objects.equals(type, StockReleasedEvent.class.getName())) {
                StockReleasedEvent event = getType(inbox.getPayload(), StockReleasedEvent.class, version);
                log.info("Stock released for orderId={} (sku={})", event.orderId(), event.sku());
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
