package com.template.starter.saga.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.saga.AsyncSagaStepHandler;
import com.template.messaging.saga.SagaStatus;
import com.template.messaging.saga.SagaStepHandler;
import com.template.messaging.saga.SagaStepHandler.StepOutcome;
import com.template.messaging.saga.StepResult;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.entity.SagaStepExecution;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for the async (suspend/resume) capability of {@link SagaOrchestrator}, using the same
 * mocked-repository + in-memory-store harness as {@link SagaOrchestratorTest} (no DB needed).
 * Steps are fakes — no real workflow is wired here.
 */
class AsyncSagaOrchestratorTest {

    private SagaInstanceRepository sagaRepository;
    private SagaOrchestrator orchestrator;
    private Map<UUID, SagaInstance> sagaStore;

    record TestContext(String orderId, boolean replied) {
        TestContext withReplied(boolean r) {
            return new TestContext(orderId, r);
        }
    }

    record TestReply(boolean ok, String note) {}

    @BeforeEach
    void setUp() {
        sagaRepository = mock(SagaInstanceRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SagaProperties properties = new SagaProperties();
        properties.setTimeout(Duration.ofMinutes(30));
        properties.setMaxRetries(3);

        PlatformTransactionManager mockTxManager = mock(PlatformTransactionManager.class);
        when(mockTxManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TransactionTemplate txTemplate = new TransactionTemplate(mockTxManager);

        orchestrator = new SagaOrchestrator(sagaRepository, objectMapper, properties, txTemplate);

        sagaStore = new ConcurrentHashMap<>();

        when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(invocation -> {
            SagaInstance saga = invocation.getArgument(0);
            if (saga.getId() == null) {
                saga.setId(UUID.randomUUID());
            }
            for (SagaStepExecution step : saga.getSteps()) {
                if (step.getId() == null) {
                    step.setId(UUID.randomUUID());
                }
            }
            sagaStore.put(saga.getId(), saga);
            return saga;
        });

        when(sagaRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sagaStore.get(invocation.getArgument(0))));

        when(sagaRepository.findByStatusAndAwaitCorrelationKey(any(SagaStatus.class), anyString()))
                .thenAnswer(invocation -> {
                    SagaStatus status = invocation.getArgument(0);
                    String key = invocation.getArgument(1);
                    return sagaStore.values().stream()
                            .filter(s -> s.getStatus() == status && key.equals(s.getAwaitCorrelationKey()))
                            .toList();
                });
    }

    @Test
    void start_asyncStepSuspends_sagaWaitsAndThreadIsReleased() {
        AsyncSagaStepHandler<TestContext, TestReply> asyncStep =
                mockAsyncHandler("corr-1", new TestContext("order-1", false),
                        null, StepResult.success());
        SagaStepHandler<TestContext> syncStep =
                mockHandler(StepOutcome.success(new TestContext("order-1", true)), StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("AsyncSaga", TestContext.class)
                .step("await-reply", asyncStep)
                .step("finalize", syncStep)
                .build();

        // start() returns (thread released) even though the saga is parked awaiting a reply.
        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", false));

        SagaInstance saga = sagaStore.get(sagaId);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.WAITING_FOR_REPLY);
        assertThat(saga.getAwaitCorrelationKey()).isEqualTo("corr-1");
        assertThat(saga.getAwaitStepName()).isEqualTo("await-reply");
        assertThat(saga.getCurrentStep()).isEqualTo(0); // still on the async step
        verify(asyncStep).execute(any());
        verify(syncStep, never()).execute(any()); // not reached until reply
    }

    @Test
    void resumeWithReply_success_sagaAdvancesAndCompletes() {
        AsyncSagaStepHandler<TestContext, TestReply> asyncStep =
                mockAsyncHandler("corr-1", new TestContext("order-1", false),
                        StepOutcome.success(new TestContext("order-1", true)), StepResult.success());
        SagaStepHandler<TestContext> syncStep =
                mockHandler(StepOutcome.success(new TestContext("order-1", true)), StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("AsyncSaga", TestContext.class)
                .step("await-reply", asyncStep)
                .step("finalize", syncStep)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", false));
        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.WAITING_FOR_REPLY);

        orchestrator.resumeWithReply("corr-1", new TestReply(true, "reserved"));

        SagaInstance saga = sagaStore.get(sagaId);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getAwaitCorrelationKey()).isNull();
        verify(asyncStep).onReply(any(), any());
        verify(syncStep).execute(any()); // ran after the reply
    }

    @Test
    void resumeWithReply_failure_compensatesPriorSteps() {
        SagaStepHandler<TestContext> syncStep =
                mockHandler(StepOutcome.success(new TestContext("order-1", false)), StepResult.success());
        AsyncSagaStepHandler<TestContext, TestReply> asyncStep =
                mockAsyncHandler("corr-1", new TestContext("order-1", false),
                        StepOutcome.failure("reservation rejected"), StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("AsyncSaga", TestContext.class)
                .step("reserve", syncStep)        // succeeds synchronously
                .step("await-reply", asyncStep)   // suspends, then fails on reply
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", false));
        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.WAITING_FOR_REPLY);

        orchestrator.resumeWithReply("corr-1", new TestReply(false, "no stock"));

        SagaInstance saga = sagaStore.get(sagaId);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(asyncStep).onReply(any(), any());
        verify(syncStep).compensate(any());      // prior succeeded step compensated
        verify(asyncStep, never()).compensate(any()); // the failed async step is not compensated
    }

    @Test
    void resumeWithReply_secondReplyForSameKey_isNoOp() {
        AsyncSagaStepHandler<TestContext, TestReply> asyncStep =
                mockAsyncHandler("corr-1", new TestContext("order-1", false),
                        StepOutcome.success(new TestContext("order-1", true)), StepResult.success());
        SagaStepHandler<TestContext> syncStep =
                mockHandler(StepOutcome.success(new TestContext("order-1", true)), StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("AsyncSaga", TestContext.class)
                .step("await-reply", asyncStep)
                .step("finalize", syncStep)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", false));

        orchestrator.resumeWithReply("corr-1", new TestReply(true, "reserved"));
        SagaStatus afterFirst = sagaStore.get(sagaId).getStatus();

        // Duplicate reply (at-least-once delivery) — must be a no-op.
        orchestrator.resumeWithReply("corr-1", new TestReply(true, "reserved-again"));

        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(afterFirst).isEqualTo(SagaStatus.COMPLETED);
        verify(asyncStep, times(1)).onReply(any(), any()); // only handled once
    }

    @Test
    void resumeWithReply_unknownKey_isNoOp() {
        // No saga parked on this key — must not throw.
        orchestrator.resumeWithReply("does-not-exist", new TestReply(true, "x"));
        assertThat(sagaStore).isEmpty();
    }

    @Test
    void regression_fullySyncSaga_stillCompletesEndToEnd() {
        SagaStepHandler<TestContext> step1 =
                mockHandler(StepOutcome.success(new TestContext("order-1", false)), StepResult.success());
        SagaStepHandler<TestContext> step2 =
                mockHandler(StepOutcome.success(new TestContext("order-1", true)), StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("SyncSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", false));

        assertThat(sagaStore.get(sagaId).getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(step1).execute(any());
        verify(step2).execute(any());
        verify(step1, never()).compensate(any());
    }

    @SuppressWarnings("unchecked")
    private AsyncSagaStepHandler<TestContext, TestReply> mockAsyncHandler(
            String correlationKey,
            TestContext suspendContext,
            StepOutcome<TestContext> onReplyOutcome,
            StepResult compensateResult) {
        AsyncSagaStepHandler<TestContext, TestReply> handler = mock(AsyncSagaStepHandler.class);
        when(handler.execute(any())).thenReturn(StepOutcome.suspend(correlationKey, suspendContext));
        when(handler.replyType()).thenReturn(TestReply.class);
        if (onReplyOutcome != null) {
            when(handler.onReply(any(), any())).thenReturn(onReplyOutcome);
        }
        if (compensateResult != null) {
            when(handler.compensate(any())).thenReturn(compensateResult);
        }
        return handler;
    }

    @SuppressWarnings("unchecked")
    private SagaStepHandler<TestContext> mockHandler(StepOutcome<TestContext> executeResult,
                                                     StepResult compensateResult) {
        SagaStepHandler<TestContext> handler = mock(SagaStepHandler.class);
        when(handler.execute(any())).thenReturn(executeResult);
        if (compensateResult != null) {
            when(handler.compensate(any())).thenReturn(compensateResult);
        }
        return handler;
    }
}
