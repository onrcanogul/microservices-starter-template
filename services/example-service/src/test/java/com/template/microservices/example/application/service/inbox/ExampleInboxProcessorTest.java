package com.template.microservices.example.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.saga.SagaStatus;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import com.template.starter.saga.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Routing tests for {@link ExampleInboxProcessor}: a stock reply drives an orchestrated saga when one
 * is parked on the orderId, otherwise it follows the Phase 1 choreography path. The two paths are
 * mutually exclusive — no double-processing.
 */
class ExampleInboxProcessorTest {

    private InboxRepository inboxRepository;
    private OrderService orderService;
    private SagaInstanceRepository sagaInstanceRepository;
    private SagaOrchestrator sagaOrchestrator;
    private ObjectMapper objectMapper;
    private ExampleInboxProcessor processor;

    @BeforeEach
    void setUp() {
        inboxRepository = mock(InboxRepository.class);
        orderService = mock(OrderService.class);
        sagaInstanceRepository = mock(SagaInstanceRepository.class);
        sagaOrchestrator = mock(SagaOrchestrator.class);
        objectMapper = new ObjectMapper();
        processor = new ExampleInboxProcessor(inboxRepository, objectMapper, orderService,
                EventUpcastChain.empty(), sagaInstanceRepository, sagaOrchestrator);
    }

    private Inbox inboxOf(String type, Object event) throws Exception {
        return Inbox.builder()
                .idempotentToken(UUID.randomUUID())
                .type(type)
                .payload(objectMapper.writeValueAsString(event))
                .processed(false)
                .version(1)
                .receivedAt(Instant.now())
                .build();
    }

    @Test
    void stockReserved_noWaitingSaga_followsChoreography() throws Exception {
        StockReservedEvent event = new StockReservedEvent(5L, "SKU-1", 3);
        when(inboxRepository.findByProcessedFalse())
                .thenReturn(List.of(inboxOf(StockReservedEvent.class.getName(), event)));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "5"))
                .thenReturn(List.of());

        processor.process();

        verify(orderService).markConfirmed(5L);
        verify(sagaOrchestrator, never()).resumeWithReply(any(), any());
    }

    @Test
    void stockReserved_waitingSaga_routesToOrchestrator() throws Exception {
        StockReservedEvent event = new StockReservedEvent(5L, "SKU-1", 3);
        when(inboxRepository.findByProcessedFalse())
                .thenReturn(List.of(inboxOf(StockReservedEvent.class.getName(), event)));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "5"))
                .thenReturn(List.of(new SagaInstance()));

        processor.process();

        verify(sagaOrchestrator).resumeWithReply(eq("5"), any(StockReservedEvent.class));
        verify(orderService, never()).markConfirmed(any());
    }

    @Test
    void stockReservationFailed_noWaitingSaga_followsChoreography() throws Exception {
        StockReservationFailedEvent event = new StockReservationFailedEvent(7L, "SKU-1", "no stock");
        when(inboxRepository.findByProcessedFalse())
                .thenReturn(List.of(inboxOf(StockReservationFailedEvent.class.getName(), event)));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "7"))
                .thenReturn(List.of());

        processor.process();

        verify(orderService).markRejected(7L);
        verify(sagaOrchestrator, never()).resumeWithReply(any(), any());
    }

    @Test
    void stockReservationFailed_waitingSaga_routesToOrchestrator() throws Exception {
        StockReservationFailedEvent event = new StockReservationFailedEvent(7L, "SKU-1", "no stock");
        when(inboxRepository.findByProcessedFalse())
                .thenReturn(List.of(inboxOf(StockReservationFailedEvent.class.getName(), event)));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "7"))
                .thenReturn(List.of(new SagaInstance()));

        processor.process();

        verify(sagaOrchestrator).resumeWithReply(eq("7"), any(StockReservationFailedEvent.class));
        verify(orderService, never()).markRejected(any());
    }
}
