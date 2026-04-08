package com.template.starter.saga.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.saga.SagaStatus;
import com.template.messaging.saga.SagaStepHandler;
import com.template.messaging.saga.SagaStepHandler.StepOutcome;
import com.template.messaging.saga.StepResult;
import com.template.messaging.saga.StepStatus;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.entity.SagaStepExecution;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SagaOrchestratorTest {

    private SagaInstanceRepository sagaRepository;
    private SagaOrchestrator orchestrator;
    private ObjectMapper objectMapper;
    private SagaProperties properties;
    private Map<UUID, SagaInstance> sagaStore;

    record TestContext(String orderId, int amount, boolean step1Done, boolean step2Done) {
        TestContext(String orderId, int amount) {
            this(orderId, amount, false, false);
        }

        TestContext withStep1Done(boolean done) {
            return new TestContext(orderId, amount, done, step2Done);
        }

        TestContext withStep2Done(boolean done) {
            return new TestContext(orderId, amount, step1Done, done);
        }
    }

    @BeforeEach
    void setUp() {
        sagaRepository = mock(SagaInstanceRepository.class);
        objectMapper = new ObjectMapper();
        properties = new SagaProperties();
        properties.setTimeout(Duration.ofMinutes(30));
        properties.setMaxRetries(3);

        // Real TransactionTemplate with mock PlatformTransactionManager — executes callbacks directly
        PlatformTransactionManager mockTxManager = mock(PlatformTransactionManager.class);
        when(mockTxManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TransactionTemplate txTemplate = new TransactionTemplate(mockTxManager);

        orchestrator = new SagaOrchestrator(sagaRepository, objectMapper, properties, txTemplate);

        // In-memory store so findById returns whatever was last saved
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

        when(sagaRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(sagaStore.get(id));
        });
    }

    @Test
    void start_allStepsSucceed_sagaCompleted() {
        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success("step1-done", new TestContext("order-1", 100, true, false)),
                null);
        SagaStepHandler<TestContext> step2 = mockHandler(
                StepOutcome.success("step2-done", new TestContext("order-1", 100, true, true)),
                null);

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", 100));

        assertThat(sagaId).isNotNull();
        verify(step1).execute(any(TestContext.class));
        verify(step2).execute(any(TestContext.class));
        verify(step1, never()).compensate(any());
        verify(step2, never()).compensate(any());

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, atLeastOnce()).save(captor.capture());
        SagaInstance finalSaga = captor.getAllValues().getLast();
        assertThat(finalSaga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void start_secondStepFails_firstStepCompensated() {
        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success("step1-done", new TestContext("order-1", 100, true, false)),
                StepResult.success()
        );
        SagaStepHandler<TestContext> step2 = mockHandler(
                StepOutcome.failure("payment failed"),
                StepResult.success()
        );

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", 100));

        assertThat(sagaId).isNotNull();
        verify(step1).execute(any(TestContext.class));
        verify(step2).execute(any(TestContext.class));
        verify(step1).compensate(any(TestContext.class)); // step-1 compensated
        verify(step2, never()).compensate(any()); // step-2 failed, not compensated

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, atLeastOnce()).save(captor.capture());
        SagaInstance finalSaga = captor.getAllValues().getLast();
        assertThat(finalSaga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    void start_thirdStepFails_firstTwoCompensatedInReverseOrder() {
        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, false)),
                StepResult.success());
        SagaStepHandler<TestContext> step2 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, true)),
                StepResult.success());
        SagaStepHandler<TestContext> step3 = mockHandler(
                StepOutcome.failure("boom"),
                StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .step("step-3", step3)
                .build();

        orchestrator.start(definition, new TestContext("order-1", 100));

        verify(step1).compensate(any(TestContext.class));
        verify(step2).compensate(any(TestContext.class));
        verify(step3, never()).compensate(any());
    }

    @Test
    void start_compensationFails_sagaMarkedFailed() {
        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, false)),
                StepResult.failure("compensation failed")
        );
        SagaStepHandler<TestContext> step2 = mockHandler(
                StepOutcome.failure("step2 error"),
                StepResult.success()
        );

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        orchestrator.start(definition, new TestContext("order-1", 100));

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, atLeastOnce()).save(captor.capture());
        SagaInstance finalSaga = captor.getAllValues().getLast();
        assertThat(finalSaga.getStatus()).isEqualTo(SagaStatus.FAILED);
    }

    @Test
    void start_singleStep_completesSuccessfully() {
        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success("done", new TestContext("order-1", 50, true, false)),
                null);

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("SingleStepSaga", TestContext.class)
                .step("only-step", step1)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", 50));

        assertThat(sagaId).isNotNull();
        verify(step1).execute(any(TestContext.class));

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getLast().getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void start_stepThrowsException_treatedAsFailure() {
        @SuppressWarnings("unchecked")
        SagaStepHandler<TestContext> step1 = mock(SagaStepHandler.class);
        when(step1.execute(any())).thenThrow(new RuntimeException("unexpected error"));

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("ExceptionSaga", TestContext.class)
                .step("throwing-step", step1)
                .build();

        UUID sagaId = orchestrator.start(definition, new TestContext("order-1", 100));
        assertThat(sagaId).isNotNull();

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, atLeastOnce()).save(captor.capture());
        SagaStatus status = captor.getAllValues().getLast().getStatus();
        assertThat(status).isIn(SagaStatus.COMPENSATED, SagaStatus.FAILED);
    }

    @Test
    void start_contextPassedBetweenSteps() {
        @SuppressWarnings("unchecked")
        SagaStepHandler<TestContext> step1 = mock(SagaStepHandler.class);
        when(step1.execute(any())).thenAnswer(inv -> {
            TestContext ctx = inv.getArgument(0);
            return StepOutcome.success("reserved", ctx.withStep1Done(true));
        });

        @SuppressWarnings("unchecked")
        SagaStepHandler<TestContext> step2 = mock(SagaStepHandler.class);
        when(step2.execute(any())).thenAnswer(inv -> {
            TestContext ctx = inv.getArgument(0);
            assertThat(ctx.step1Done()).isTrue();
            return StepOutcome.success("charged", ctx.withStep2Done(true));
        });

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("ContextSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        orchestrator.start(definition, new TestContext("order-1", 100));

        // Verify step2 received the updated context from step1
        ArgumentCaptor<TestContext> contextCaptor = ArgumentCaptor.forClass(TestContext.class);
        verify(step2).execute(contextCaptor.capture());
        assertThat(contextCaptor.getValue().step1Done()).isTrue();
    }

    @Test
    void resume_runningSaga_resumesFromCurrentStep() {
        UUID sagaId = UUID.randomUUID();
        SagaInstance existingSaga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("TestSaga")
                .correlationId(UUID.randomUUID())
                .status(SagaStatus.RUNNING)
                .payload("{\"orderId\":\"order-1\",\"amount\":100,\"step1Done\":true,\"step2Done\":false}")
                .currentStep(1)
                .retryCount(0)
                .steps(new ArrayList<>())
                .build();

        SagaStepExecution step0Exec = SagaStepExecution.builder()
                .id(UUID.randomUUID())
                .sagaInstance(existingSaga)
                .stepName("step-1")
                .stepOrder(0)
                .status(StepStatus.SUCCEEDED)
                .build();
        existingSaga.getSteps().add(step0Exec);

        when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(existingSaga));

        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, false)),
                StepResult.success());
        SagaStepHandler<TestContext> step2 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, true)),
                StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        orchestrator.resume(sagaId, definition);

        verify(step1, never()).execute(any());
        verify(step2).execute(any(TestContext.class));
    }

    @Test
    void resume_compensatingSaga_runsCompensation() {
        UUID sagaId = UUID.randomUUID();
        SagaInstance existingSaga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("TestSaga")
                .correlationId(UUID.randomUUID())
                .status(SagaStatus.COMPENSATING)
                .payload("{\"orderId\":\"order-1\",\"amount\":100,\"step1Done\":true,\"step2Done\":false}")
                .currentStep(1)
                .retryCount(0)
                .steps(new ArrayList<>())
                .build();

        SagaStepExecution step0Exec = SagaStepExecution.builder()
                .id(UUID.randomUUID())
                .sagaInstance(existingSaga)
                .stepName("step-1")
                .stepOrder(0)
                .status(StepStatus.SUCCEEDED)
                .build();
        existingSaga.getSteps().add(step0Exec);

        when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(existingSaga));

        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, false)),
                StepResult.success());
        SagaStepHandler<TestContext> step2 = mockHandler(
                StepOutcome.failure("fail"),
                StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .step("step-2", step2)
                .build();

        orchestrator.resume(sagaId, definition);

        // Step-1 should be compensated since it was SUCCEEDED
        verify(step1).compensate(any(TestContext.class));
        // Execute should NOT be called (saga was COMPENSATING, not RUNNING)
        verify(step1, never()).execute(any());
        verify(step2, never()).execute(any());
    }

    @Test
    void resume_sagaNotFound_throwsException() {
        UUID sagaId = UUID.randomUUID();
        when(sagaRepository.findById(sagaId)).thenReturn(Optional.empty());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", mockHandler(StepOutcome.success(new TestContext("o", 1)), null))
                .build();

        assertThatThrownBy(() -> orchestrator.resume(sagaId, definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Saga not found");
    }

    @Test
    void resume_typeMismatch_throwsException() {
        UUID sagaId = UUID.randomUUID();
        SagaInstance existingSaga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("DifferentSaga")
                .correlationId(UUID.randomUUID())
                .status(SagaStatus.RUNNING)
                .payload("{}")
                .currentStep(0)
                .retryCount(0)
                .steps(new ArrayList<>())
                .build();

        when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(existingSaga));

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", mockHandler(StepOutcome.success(new TestContext("o", 1)), null))
                .build();

        assertThatThrownBy(() -> orchestrator.resume(sagaId, definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type mismatch");
    }

    @Test
    void resumeById_registeredDefinition_resumesSaga() {
        UUID sagaId = UUID.randomUUID();
        SagaInstance existingSaga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("TestSaga")
                .correlationId(UUID.randomUUID())
                .status(SagaStatus.RUNNING)
                .payload("{\"orderId\":\"order-1\",\"amount\":100,\"step1Done\":false,\"step2Done\":false}")
                .currentStep(0)
                .retryCount(0)
                .steps(new ArrayList<>())
                .build();

        when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(existingSaga));

        SagaStepHandler<TestContext> step1 = mockHandler(
                StepOutcome.success(new TestContext("order-1", 100, true, false)),
                StepResult.success());

        SagaDefinition<TestContext> definition = SagaDefinition
                .builder("TestSaga", TestContext.class)
                .step("step-1", step1)
                .build();

        // Register definition first
        orchestrator.register(definition);

        orchestrator.resumeById(sagaId);

        verify(step1).execute(any(TestContext.class));
    }

    @Test
    void resumeById_noDefinitionRegistered_throwsException() {
        UUID sagaId = UUID.randomUUID();
        SagaInstance existingSaga = SagaInstance.builder()
                .id(sagaId)
                .sagaType("UnknownSaga")
                .correlationId(UUID.randomUUID())
                .status(SagaStatus.RUNNING)
                .payload("{}")
                .currentStep(0)
                .retryCount(0)
                .steps(new ArrayList<>())
                .build();

        when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(existingSaga));

        assertThatThrownBy(() -> orchestrator.resumeById(sagaId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No definition registered");
    }

    @Test
    void build_noSteps_throwsIllegalStateException() {
        assertThatThrownBy(() -> SagaDefinition
                .builder("EmptySaga", TestContext.class)
                .build())
                .isInstanceOf(IllegalStateException.class);
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
