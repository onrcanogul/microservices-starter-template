package com.template.microservices.example.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.event.stock.StockReleasedEvent;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.saga.SagaStatus;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.infrastructure.messaging.PaymentFailedEvent;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxProcessor;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import com.template.starter.saga.repository.SagaInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Drains the inbox. Stock-reservation replies are ROUTED: if an orchestrated saga is parked
 * (WAITING_FOR_REPLY) on this orderId, the reply drives the saga via
 * {@link SagaOrchestrator#resumeWithReply}; otherwise the Phase 1 choreography behaviour runs
 * (update order status). One event set, two flows, no double-processing.
 */
@Service
@Slf4j
public class ExampleInboxProcessor extends InboxProcessor {
    private final InboxRepository inboxRepository;
    private final OrderService orderService;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public ExampleInboxProcessor(
            InboxRepository inboxRepository,
            ObjectMapper objectMapper,
            OrderService orderService,
            EventUpcastChain upcastChain,
            SagaInstanceRepository sagaInstanceRepository,
            SagaOrchestrator sagaOrchestrator
    ) {
        super(objectMapper, upcastChain);
        this.inboxRepository = inboxRepository;
        this.orderService = orderService;
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaOrchestrator = sagaOrchestrator;
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
                if (!routedToSaga(event.orderId(), event)) {
                    orderService.markConfirmed(event.orderId());   // choreography
                }
            }
            else if (Objects.equals(type, StockReservationFailedEvent.class.getName())) {
                StockReservationFailedEvent event = getType(inbox.getPayload(), StockReservationFailedEvent.class, version);
                if (!routedToSaga(event.orderId(), event)) {
                    log.warn("Stock reservation failed for orderId={}: {}", event.orderId(), event.reason());
                    orderService.markRejected(event.orderId());    // choreography
                }
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

    /**
     * If an orchestrated saga is parked on this orderId, deliver the reply to it and report true.
     * Otherwise report false so the caller runs the choreography path.
     */
    private boolean routedToSaga(Long orderId, Event reply) {
        String correlationKey = String.valueOf(orderId);
        boolean waiting = !sagaInstanceRepository
                .findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, correlationKey)
                .isEmpty();
        if (waiting) {
            sagaOrchestrator.resumeWithReply(correlationKey, reply);
            return true;
        }
        return false;
    }
}
