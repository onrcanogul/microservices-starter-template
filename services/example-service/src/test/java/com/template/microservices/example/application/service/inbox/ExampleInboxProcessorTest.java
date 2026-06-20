package com.template.microservices.example.application.service.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.saga.SagaStatus;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.property.InboxProperties;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxProcessingSupport;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import com.template.starter.saga.repository.SagaInstanceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Routing tests for {@link ExampleInboxProcessor}: a stock reply drives an orchestrated saga when one
 * is parked on the orderId, otherwise it follows the choreography path. The two paths are mutually
 * exclusive — no double-processing. Polling / per-message TX live in {@code InboxProcessingSupport}
 * (wired here with a mock repository and an inline transaction template).
 */
class ExampleInboxProcessorTest {

    private InboxRepository inboxRepository;
    private OrderService orderService;
    private SagaInstanceRepository sagaInstanceRepository;
    private SagaOrchestrator sagaOrchestrator;
    private ObjectMapper objectMapper;
    private ExampleInboxProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        inboxRepository = mock(InboxRepository.class);
        orderService = mock(OrderService.class);
        sagaInstanceRepository = mock(SagaInstanceRepository.class);
        sagaOrchestrator = mock(SagaOrchestrator.class);
        objectMapper = new ObjectMapper();

        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        when(meterProvider.getIfAvailable()).thenReturn(null);
        InboxProcessingSupport support = new InboxProcessingSupport(
                inboxRepository, new TransactionTemplate(ptm), new InboxProperties(), meterProvider);

        processor = new ExampleInboxProcessor(support, objectMapper, orderService,
                EventUpcastChain.empty(), sagaInstanceRepository, sagaOrchestrator);
    }

    private void singleInbox(String type, Object event) throws Exception {
        Inbox inbox = Inbox.builder()
                .idempotentToken(UUID.randomUUID())
                .type(type)
                .payload(objectMapper.writeValueAsString(event))
                .processed(false)
                .version(1)
                .receivedAt(Instant.now())
                .build();
        when(inboxRepository.findEligible(any(), any())).thenReturn(List.of(inbox));
        when(inboxRepository.findById(any())).thenReturn(Optional.of(inbox));
    }

    @Test
    void stockReserved_noWaitingSaga_followsChoreography() throws Exception {
        singleInbox(StockReservedEvent.class.getName(), new StockReservedEvent(5L, "SKU-1", 3));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "5"))
                .thenReturn(List.of());

        processor.process();

        verify(orderService).markConfirmed(5L);
        verify(sagaOrchestrator, never()).resumeWithReply(any(), any());
    }

    @Test
    void stockReserved_waitingSaga_routesToOrchestrator() throws Exception {
        singleInbox(StockReservedEvent.class.getName(), new StockReservedEvent(5L, "SKU-1", 3));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "5"))
                .thenReturn(List.of(new SagaInstance()));

        processor.process();

        verify(sagaOrchestrator).resumeWithReply(eq("5"), any(StockReservedEvent.class));
        verify(orderService, never()).markConfirmed(any());
    }

    @Test
    void stockReservationFailed_noWaitingSaga_followsChoreography() throws Exception {
        singleInbox(StockReservationFailedEvent.class.getName(), new StockReservationFailedEvent(7L, "SKU-1", "no stock"));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "7"))
                .thenReturn(List.of());

        processor.process();

        verify(orderService).markRejected(7L);
        verify(sagaOrchestrator, never()).resumeWithReply(any(), any());
    }

    @Test
    void stockReservationFailed_waitingSaga_routesToOrchestrator() throws Exception {
        singleInbox(StockReservationFailedEvent.class.getName(), new StockReservationFailedEvent(7L, "SKU-1", "no stock"));
        when(sagaInstanceRepository.findByStatusAndAwaitCorrelationKey(SagaStatus.WAITING_FOR_REPLY, "7"))
                .thenReturn(List.of(new SagaInstance()));

        processor.process();

        verify(sagaOrchestrator).resumeWithReply(eq("7"), any(StockReservationFailedEvent.class));
        verify(orderService, never()).markRejected(any());
    }
}
