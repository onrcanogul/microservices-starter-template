package com.template.microservices.example.application.service.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.saga.SagaStatus;
import com.template.messaging.saga.SagaStepHandler;
import com.template.messaging.saga.SagaStepHandler.StepOutcome;
import com.template.messaging.saga.StepResult;
import com.template.microservices.example.infrastructure.messaging.processor.OrderCreatedProducer;
import com.template.microservices.example.infrastructure.messaging.processor.StockReleaseRequestedProducer;
import com.template.microservices.example.infrastructure.messaging.processor.StockReservationRequestedProducer;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.service.OutboxService;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.entity.SagaStepExecution;
import com.template.starter.saga.orchestration.SagaDefinition;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Exercises the orchestrated-async create-order saga end to end with the REAL example steps and
 * producers, replacing only persistence with the in-memory mock harness the other saga tests use
 * (no real Kafka — the inventory reply is simulated by calling {@code resumeWithReply}).
 */
class CreateOrderAsyncSagaTest {

    private SagaInstanceRepository sagaRepository;
    private OutboxRepository outboxRepository;
    private SagaOrchestrator orchestrator;
    private ReserveStockStep reserveStockStep;
    private ChargePaymentStep chargePaymentStep;
    private ConfirmOrderStep confirmOrderStep;
    private Map<UUID, SagaInstance> sagaStore;

    private CreateOrderSagaContext ctx() {
        return new CreateOrderSagaContext(5L, "SKU-1", 3, "a@b.com", false, false);
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        sagaRepository = mock(SagaInstanceRepository.class);
        outboxRepository = mock(OutboxRepository.class);

        OutboxService outboxService = new OutboxService(outboxRepository, objectMapper);
        reserveStockStep = new ReserveStockStep(
                new StockReservationRequestedProducer(outboxService),
                new StockReleaseRequestedProducer(outboxService));
        chargePaymentStep = new ChargePaymentStep();
        confirmOrderStep = new ConfirmOrderStep(new OrderCreatedProducer(outboxService));

        SagaProperties properties = new SagaProperties();
        properties.setTimeout(Duration.ofMinutes(30));
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        orchestrator = new SagaOrchestrator(sagaRepository, objectMapper, properties, new TransactionTemplate(txm));

        sagaStore = new ConcurrentHashMap<>();
        when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(inv -> {
            SagaInstance s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            for (SagaStepExecution st : s.getSteps()) if (st.getId() == null) st.setId(UUID.randomUUID());
            sagaStore.put(s.getId(), s);
            return s;
        });
        when(sagaRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(sagaStore.get(inv.getArgument(0))));
        when(sagaRepository.findByStatusAndAwaitCorrelationKey(any(SagaStatus.class), anyString()))
                .thenAnswer(inv -> {
                    SagaStatus st = inv.getArgument(0);
                    String key = inv.getArgument(1);
                    return sagaStore.values().stream()
                            .filter(s -> s.getStatus() == st && key.equals(s.getAwaitCorrelationKey()))
                            .toList();
                });
    }

    private SagaDefinition<CreateOrderSagaContext> fullDefinition() {
        return SagaDefinition.builder("CreateOrderSaga", CreateOrderSagaContext.class)
                .step("reserve-stock", reserveStockStep)
                .step("charge-payment", chargePaymentStep)
                .step("confirm-order", confirmOrderStep)
                .build();
    }

    private List<String> outboxDestinations() {
        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(Outbox::getDestination).toList();
    }

    @Test
    void happyPath_reserveStockSuspends_thenReplyCompletesSaga() {
        UUID sagaId = orchestrator.start(fullDefinition(), ctx());

        SagaInstance saga = sagaStore.get(sagaId);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.WAITING_FOR_REPLY);
        assertThat(saga.getAwaitCorrelationKey()).isEqualTo("5");
        assertThat(saga.getAwaitStepName()).isEqualTo("reserve-stock");
        assertThat(outboxDestinations()).contains("stock.reservation.requested");

        orchestrator.resumeWithReply("5", new StockReservedEvent(5L, "SKU-1", 3));

        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void failedReply_compensatesSaga() {
        UUID sagaId = orchestrator.start(fullDefinition(), ctx());
        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.WAITING_FOR_REPLY);

        orchestrator.resumeWithReply("5", new StockReservationFailedEvent(5L, "SKU-1", "no stock"));

        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    void laterStepFails_reserveCompensationPublishesRelease() {
        @SuppressWarnings("unchecked")
        SagaStepHandler<CreateOrderSagaContext> failingStep = mock(SagaStepHandler.class);
        when(failingStep.execute(any())).thenReturn(StepOutcome.failure("charge failed"));
        when(failingStep.compensate(any())).thenReturn(StepResult.success());

        SagaDefinition<CreateOrderSagaContext> definition =
                SagaDefinition.builder("CreateOrderSaga", CreateOrderSagaContext.class)
                        .step("reserve-stock", reserveStockStep)
                        .step("charge-payment", failingStep)
                        .build();

        UUID sagaId = orchestrator.start(definition, ctx());
        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.WAITING_FOR_REPLY);

        // reply succeeds -> reserve SUCCEEDED -> next step fails -> reserve compensation publishes release
        orchestrator.resumeWithReply("5", new StockReservedEvent(5L, "SKU-1", 3));

        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        // The failed step (charge-payment) is NOT compensated; the succeeded reserve step IS — and its
        // compensation publishes stock.release.requested (real cross-service compensation).
        assertThat(outboxDestinations())
                .contains("stock.reservation.requested", "stock.release.requested");
    }
}
