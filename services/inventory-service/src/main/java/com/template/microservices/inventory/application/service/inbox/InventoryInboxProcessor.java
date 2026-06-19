package com.template.microservices.inventory.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.stock.StockReleaseRequestedEvent;
import com.template.messaging.event.stock.StockReleasedEvent;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservationRequestedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.microservices.inventory.application.service.inventory.InventoryService;
import com.template.microservices.inventory.infrastructure.messaging.processor.StockReleasedProducer;
import com.template.microservices.inventory.infrastructure.messaging.processor.StockReservationFailedProducer;
import com.template.microservices.inventory.infrastructure.messaging.processor.StockReservedProducer;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Inventory side of the choreographed stock-reservation saga. Branches on the inbox row's stored
 * type (event FQCN) and emits the reply event through the outbox — all in one transaction, so the
 * stock mutation and the outbox row commit atomically.
 */
@Service
public class InventoryInboxProcessor extends InboxProcessor {

    private static final String REASON_INSUFFICIENT = "INSUFFICIENT_STOCK";

    private final InboxRepository inboxRepository;
    private final InventoryService inventoryService;
    private final StockReservedProducer stockReservedProducer;
    private final StockReservationFailedProducer stockReservationFailedProducer;
    private final StockReleasedProducer stockReleasedProducer;

    public InventoryInboxProcessor(
            InboxRepository inboxRepository,
            ObjectMapper objectMapper,
            EventUpcastChain upcastChain,
            InventoryService inventoryService,
            StockReservedProducer stockReservedProducer,
            StockReservationFailedProducer stockReservationFailedProducer,
            StockReleasedProducer stockReleasedProducer
    ) {
        super(objectMapper, upcastChain);
        this.inboxRepository = inboxRepository;
        this.inventoryService = inventoryService;
        this.stockReservedProducer = stockReservedProducer;
        this.stockReservationFailedProducer = stockReservationFailedProducer;
        this.stockReleasedProducer = stockReleasedProducer;
    }

    @Transactional
    public void process() {
        List<Inbox> inboxes = inboxRepository.findByProcessedFalse();
        for (Inbox inbox : inboxes) {
            String type = inbox.getType();
            int version = inbox.getVersion();

            if (Objects.equals(type, StockReservationRequestedEvent.class.getName())) {
                StockReservationRequestedEvent event =
                        getType(inbox.getPayload(), StockReservationRequestedEvent.class, version);
                boolean reserved = inventoryService.reserve(event.orderId(), event.sku(), event.amount());
                if (reserved) {
                    stockReservedProducer.process(
                            new StockReservedEvent(event.orderId(), event.sku(), event.amount()));
                } else {
                    stockReservationFailedProducer.process(
                            new StockReservationFailedEvent(event.orderId(), event.sku(), REASON_INSUFFICIENT));
                }
            } else if (Objects.equals(type, StockReleaseRequestedEvent.class.getName())) {
                StockReleaseRequestedEvent event =
                        getType(inbox.getPayload(), StockReleaseRequestedEvent.class, version);
                inventoryService.release(event.orderId());
                stockReleasedProducer.process(new StockReleasedEvent(event.orderId(), event.sku()));
            }

            inbox.setProcessed(true);
            inboxRepository.save(inbox);
        }
    }
}
