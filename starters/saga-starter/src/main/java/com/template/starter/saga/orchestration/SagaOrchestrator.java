package com.template.starter.saga.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.saga.AsyncSagaStepHandler;
import com.template.messaging.saga.SagaStatus;
import com.template.messaging.saga.SagaStepHandler.StepOutcome;
import com.template.messaging.saga.StepResult;
import com.template.messaging.saga.StepStatus;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.entity.SagaStepExecution;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes orchestration-based sagas: drives steps forward, persists state after each step,
 * and triggers reverse compensation when a step fails.
 * <p>
 * Each step executes in its own transaction, so intermediate state is committed.
 * This enables recovery of stuck sagas via {@link SagaScheduler}.
 * <p>
 * <b>Async steps:</b> a step may return {@link StepOutcome#suspend(String, Object)} after publishing a
 * request. The saga is persisted as {@link SagaStatus#WAITING_FOR_REPLY} and the calling thread is
 * released. When the reply arrives, the service/inbox layer calls
 * {@link #resumeWithReply(String, Object)} to invoke the step's
 * {@link AsyncSagaStepHandler#onReply(Object, Object)} and continue the saga.
 * <p>
 * Usage:
 * <pre>{@code
 * SagaDefinition<OrderContext> definition = SagaDefinition
 *     .builder("CreateOrderSaga", OrderContext.class)
 *     .step("reserve-stock", reserveStockHandler)
 *     .step("charge-payment", chargePaymentHandler)
 *     .build();
 *
 * UUID sagaId = orchestrator.start(definition, new OrderContext(orderId, amount));
 * }</pre>
 */
@Slf4j
public class SagaOrchestrator {

    /** Outcome of running (or replying to) a single step, used to drive the forward loop. */
    private enum StepProgress { CONTINUE, FAIL, SUSPEND, NOOP }

    private final SagaInstanceRepository sagaRepository;
    private final ObjectMapper objectMapper;
    private final SagaProperties properties;
    private final TransactionTemplate txTemplate;
    private final Map<String, SagaDefinition<?>> definitionRegistry = new ConcurrentHashMap<>();

    public SagaOrchestrator(SagaInstanceRepository sagaRepository,
                            ObjectMapper objectMapper,
                            SagaProperties properties,
                            TransactionTemplate txTemplate) {
        this.sagaRepository = sagaRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.txTemplate = txTemplate;
    }

    /**
     * Register a saga definition so it can be looked up by type during recovery and async resume.
     */
    public void register(SagaDefinition<?> definition) {
        definitionRegistry.put(definition.sagaType(), definition);
        log.debug("Registered saga definition: {}", definition.sagaType());
    }

    /**
     * Start a new saga: persist it in its own transaction, then execute steps sequentially.
     * Each step commits independently. If any step fails, compensation runs in reverse order.
     * If a step suspends (async), this returns once the saga is parked WAITING_FOR_REPLY.
     *
     * @param definition the saga definition
     * @param context    the initial saga context
     * @param <C>        context type
     * @return the saga instance ID
     */
    public <C> UUID start(SagaDefinition<C> definition, C context) {
        register(definition);

        SagaInstance saga = txTemplate.execute(status -> {
            SagaInstance s = createSagaInstance(definition, context);
            return sagaRepository.save(s);
        });

        log.info("Saga [{}] started: id={}, steps={}",
                definition.sagaType(), saga.getId(), definition.steps().size());

        return execute(saga.getId(), definition, context);
    }

    /**
     * Resume a stuck or previously failed saga from its current step.
     *
     * @param sagaId     the saga instance ID to resume
     * @param definition the saga definition (must match the saga type)
     * @param <C>        context type
     * @return the saga instance ID
     */
    public <C> UUID resume(UUID sagaId, SagaDefinition<C> definition) {
        SagaInstance saga = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        if (!saga.getSagaType().equals(definition.sagaType())) {
            throw new IllegalArgumentException("Saga type mismatch: expected " +
                    definition.sagaType() + ", got " + saga.getSagaType());
        }

        C context = deserializeContext(saga.getPayload(), definition.contextType());

        txTemplate.executeWithoutResult(status -> {
            SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
            s.setRetryCount(s.getRetryCount() + 1);
            s.setDeadlineAt(Instant.now().plus(properties.getTimeout()));
            sagaRepository.save(s);
        });

        if (saga.getStatus() == SagaStatus.COMPENSATING) {
            compensate(sagaId, definition, context);
            return sagaId;
        }

        txTemplate.executeWithoutResult(status -> {
            SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
            s.setStatus(SagaStatus.RUNNING);
            sagaRepository.save(s);
        });

        return execute(sagaId, definition, context);
    }

    /**
     * Resume a saga by ID, looking up the definition from the internal registry.
     * Used by {@link SagaScheduler} for automatic recovery.
     *
     * @param sagaId the saga instance ID to resume
     * @return the saga instance ID
     * @throws IllegalStateException if no definition is registered for the saga type
     */
    @SuppressWarnings("unchecked")
    public UUID resumeById(UUID sagaId) {
        SagaInstance saga = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        SagaDefinition<?> definition = definitionRegistry.get(saga.getSagaType());
        if (definition == null) {
            throw new IllegalStateException("No definition registered for saga type: " + saga.getSagaType());
        }

        return resume(sagaId, (SagaDefinition) definition);
    }

    /**
     * Deliver an asynchronous reply to the saga currently parked on {@code correlationKey}, invoking
     * the awaiting {@link AsyncSagaStepHandler#onReply}. On success the saga advances to the next step
     * (running sync steps, suspending again on the next async step, or completing); on failure it
     * compensates in reverse order.
     * <p>
     * <b>Idempotent:</b> if no saga is WAITING_FOR_REPLY on this key (already resumed, timed out, or
     * unknown), this is a no-op — safe under at-least-once reply delivery.
     *
     * @param correlationKey the key the suspended step published with its request
     * @param reply          the reply payload (converted to the step's {@code replyType()})
     */
    public void resumeWithReply(String correlationKey, Object reply) {
        List<SagaInstance> parked = sagaRepository.findByStatusAndAwaitCorrelationKey(
                SagaStatus.WAITING_FOR_REPLY, correlationKey);
        if (parked.isEmpty()) {
            log.debug("No saga WAITING_FOR_REPLY for correlationKey={} — ignoring reply (idempotent)", correlationKey);
            return;
        }

        SagaInstance saga = parked.get(0);
        SagaDefinition<?> definition = definitionRegistry.get(saga.getSagaType());
        if (definition == null) {
            throw new IllegalStateException("No definition registered for saga type: " + saga.getSagaType());
        }

        doResumeWithReply(saga.getId(), definition, reply);
    }

    @SuppressWarnings("unchecked")
    private <C> void doResumeWithReply(UUID sagaId, SagaDefinition<C> definition, Object reply) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElseThrow();
        int awaitingStepIndex = saga.getCurrentStep();
        SagaDefinition.StepDefinition<C> stepDef = definition.steps().get(awaitingStepIndex);

        if (!(stepDef.handler() instanceof AsyncSagaStepHandler)) {
            throw new IllegalStateException("Step '" + stepDef.name() +
                    "' received a reply but is not an AsyncSagaStepHandler");
        }
        AsyncSagaStepHandler<C, Object> asyncHandler = (AsyncSagaStepHandler<C, Object>) stepDef.handler();

        C context = deserializeContext(saga.getPayload(), definition.contextType());
        AtomicReference<C> contextRef = new AtomicReference<>(context);

        StepProgress progress = txTemplate.execute(status -> {
            SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
            // Idempotency under concurrency: a concurrent reply or a timeout may have moved the saga on.
            if (s.getStatus() != SagaStatus.WAITING_FOR_REPLY) {
                return StepProgress.NOOP;
            }
            SagaStepExecution stepExec = getOrCreateStepExecution(s, stepDef, awaitingStepIndex);

            StepOutcome<C> outcome;
            try {
                Object typedReply = objectMapper.convertValue(reply, asyncHandler.replyType());
                outcome = asyncHandler.onReply(context, typedReply);
            } catch (Exception e) {
                outcome = StepOutcome.failure("onReply exception: " + e.getMessage(), e);
            }

            return switch (outcome.result()) {
                case StepResult.Success success -> {
                    stepExec.setStatus(StepStatus.SUCCEEDED);
                    stepExec.setOutput(success.output());
                    stepExec.setExecutedAt(Instant.now());
                    s.setCurrentStep(awaitingStepIndex + 1);
                    s.setStatus(SagaStatus.RUNNING);
                    s.setAwaitCorrelationKey(null);
                    s.setAwaitStepName(null);
                    C updatedCtx = outcome.updatedContext() != null ? outcome.updatedContext() : context;
                    contextRef.set(updatedCtx);
                    s.setPayload(serializeContext(updatedCtx));
                    s.setUpdatedAt(Instant.now());
                    sagaRepository.save(s);
                    yield StepProgress.CONTINUE;
                }
                case StepResult.Failure failure -> {
                    stepExec.setStatus(StepStatus.FAILED);
                    stepExec.setFailureReason(failure.reason());
                    stepExec.setExecutedAt(Instant.now());
                    s.setAwaitCorrelationKey(null);
                    s.setAwaitStepName(null);
                    s.setUpdatedAt(Instant.now());
                    sagaRepository.save(s);
                    yield StepProgress.FAIL;
                }
                // Single-level await: onReply must produce a terminal outcome, never suspend again.
                case StepResult.Suspended suspended ->
                        throw new IllegalStateException("AsyncSagaStepHandler.onReply must not return Suspended");
            };
        });

        switch (progress) {
            case NOOP -> log.debug("Saga [{}] no longer WAITING_FOR_REPLY — reply ignored (idempotent)", sagaId);
            case FAIL -> {
                log.warn("Saga [{}] async step '{}' failed on reply. Starting compensation.", sagaId, stepDef.name());
                compensate(sagaId, definition, contextRef.get());
            }
            case CONTINUE -> {
                log.info("Saga [{}] resumed from reply at step '{}'", sagaId, stepDef.name());
                execute(sagaId, definition, contextRef.get());
            }
            case SUSPEND -> { /* unreachable: onReply cannot suspend */ }
        }
    }

    private <C> UUID execute(UUID sagaId, SagaDefinition<C> definition, C initialContext) {
        List<SagaDefinition.StepDefinition<C>> steps = definition.steps();
        AtomicReference<C> contextRef = new AtomicReference<>(initialContext);

        SagaInstance saga = sagaRepository.findById(sagaId).orElseThrow();
        int startStep = saga.getCurrentStep();

        for (int i = startStep; i < steps.size(); i++) {
            SagaDefinition.StepDefinition<C> stepDef = steps.get(i);
            int stepIndex = i;
            C currentContext = contextRef.get();

            log.debug("Saga [{}] executing step {}/{}: {}",
                    sagaId, i + 1, steps.size(), stepDef.name());

            StepProgress progress = txTemplate.execute(status -> {
                SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
                SagaStepExecution stepExec = getOrCreateStepExecution(s, stepDef, stepIndex);

                StepOutcome<C> outcome;
                try {
                    outcome = stepDef.handler().execute(currentContext);
                } catch (Exception e) {
                    outcome = StepOutcome.failure("Unhandled exception: " + e.getMessage(), e);
                }

                return switch (outcome.result()) {
                    case StepResult.Success success -> {
                        stepExec.setStatus(StepStatus.SUCCEEDED);
                        stepExec.setOutput(success.output());
                        stepExec.setExecutedAt(Instant.now());
                        s.setCurrentStep(stepIndex + 1);
                        C updatedCtx = outcome.updatedContext() != null ? outcome.updatedContext() : currentContext;
                        contextRef.set(updatedCtx);
                        s.setPayload(serializeContext(updatedCtx));
                        s.setUpdatedAt(Instant.now());
                        sagaRepository.save(s);
                        yield StepProgress.CONTINUE;
                    }
                    case StepResult.Suspended suspended -> {
                        // Async step published its request; park the saga and release the thread.
                        stepExec.setStatus(StepStatus.AWAITING);
                        stepExec.setExecutedAt(Instant.now());
                        s.setStatus(SagaStatus.WAITING_FOR_REPLY);
                        s.setAwaitCorrelationKey(suspended.correlationKey());
                        s.setAwaitStepName(stepDef.name());
                        // currentStep stays on this step so the reply resumes the right handler.
                        C updatedCtx = outcome.updatedContext() != null ? outcome.updatedContext() : currentContext;
                        contextRef.set(updatedCtx);
                        s.setPayload(serializeContext(updatedCtx));
                        s.setUpdatedAt(Instant.now());
                        sagaRepository.save(s);
                        yield StepProgress.SUSPEND;
                    }
                    case StepResult.Failure failure -> {
                        stepExec.setStatus(StepStatus.FAILED);
                        stepExec.setFailureReason(failure.reason());
                        stepExec.setExecutedAt(Instant.now());
                        s.setUpdatedAt(Instant.now());
                        sagaRepository.save(s);
                        yield StepProgress.FAIL;
                    }
                };
            });

            if (progress == StepProgress.SUSPEND) {
                log.info("Saga [{}] step '{}' suspended — awaiting reply, releasing thread",
                        sagaId, stepDef.name());
                return sagaId;
            }
            if (progress == StepProgress.FAIL) {
                log.warn("Saga [{}] step '{}' failed. Starting compensation.", sagaId, stepDef.name());
                compensate(sagaId, definition, contextRef.get());
                return sagaId;
            }

            log.debug("Saga [{}] step '{}' succeeded", sagaId, stepDef.name());
        }

        // All steps succeeded
        txTemplate.executeWithoutResult(status -> {
            SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
            s.setStatus(SagaStatus.COMPLETED);
            s.setUpdatedAt(Instant.now());
            sagaRepository.save(s);
        });

        log.info("Saga [{}] completed successfully", sagaId);
        return sagaId;
    }

    private <C> void compensate(UUID sagaId, SagaDefinition<C> definition, C context) {
        txTemplate.executeWithoutResult(status -> {
            SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
            s.setStatus(SagaStatus.COMPENSATING);
            s.setUpdatedAt(Instant.now());
            sagaRepository.save(s);
        });

        List<SagaDefinition.StepDefinition<C>> steps = definition.steps();
        boolean compensationFailed = false;

        SagaInstance saga = sagaRepository.findById(sagaId).orElseThrow();
        int lastSucceededStep = saga.getCurrentStep() - 1;

        // Compensate in reverse order, only steps that succeeded
        for (int i = lastSucceededStep; i >= 0; i--) {
            int stepIndex = i;
            SagaStepExecution stepExec = saga.getSteps().stream()
                    .filter(s -> s.getStepOrder() == stepIndex)
                    .findFirst()
                    .orElse(null);

            if (stepExec == null || stepExec.getStatus() != StepStatus.SUCCEEDED) {
                continue;
            }

            SagaDefinition.StepDefinition<C> stepDef = steps.get(i);
            log.debug("Saga [{}] compensating step: {}", sagaId, stepDef.name());

            StepResult result;
            try {
                result = stepDef.handler().compensate(context);
            } catch (Exception e) {
                result = StepResult.failure("Compensation exception: " + e.getMessage(), e);
            }

            switch (result) {
                case StepResult.Success ignored -> {
                    int compStepIndex = stepIndex;
                    txTemplate.executeWithoutResult(status -> {
                        SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
                        SagaStepExecution se = s.getSteps().stream()
                                .filter(st -> st.getStepOrder() == compStepIndex)
                                .findFirst().orElseThrow();
                        se.setStatus(StepStatus.COMPENSATED);
                        se.setCompensatedAt(Instant.now());
                        sagaRepository.save(s);
                    });
                    log.debug("Saga [{}] step '{}' compensated successfully",
                            sagaId, stepDef.name());
                }
                case StepResult.Failure failure -> {
                    log.error("Saga [{}] compensation FAILED for step '{}': {}",
                            sagaId, stepDef.name(), failure.reason());
                    compensationFailed = true;
                }
                case StepResult.Suspended ignored -> {
                    // Compensation cannot suspend; treat as a failed compensation.
                    log.error("Saga [{}] compensation for step '{}' returned Suspended — not allowed; marking failed",
                            sagaId, stepDef.name());
                    compensationFailed = true;
                }
            }
        }

        SagaStatus finalStatus = compensationFailed ? SagaStatus.FAILED : SagaStatus.COMPENSATED;
        txTemplate.executeWithoutResult(status -> {
            SagaInstance s = sagaRepository.findById(sagaId).orElseThrow();
            s.setStatus(finalStatus);
            s.setUpdatedAt(Instant.now());
            sagaRepository.save(s);
        });

        if (compensationFailed) {
            log.error("Saga [{}] compensation incomplete — manual intervention required", sagaId);
        } else {
            log.info("Saga [{}] fully compensated", sagaId);
        }
    }

    private <C> SagaInstance createSagaInstance(SagaDefinition<C> definition, C context) {
        Instant now = Instant.now();
        SagaInstance saga = SagaInstance.builder()
                .sagaType(definition.sagaType())
                .correlationId(UUID.randomUUID())
                .status(SagaStatus.STARTED)
                .payload(serializeContext(context))
                .currentStep(0)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .deadlineAt(now.plus(properties.getTimeout()))
                .build();

        // Pre-create step execution records
        List<SagaDefinition.StepDefinition<C>> steps = definition.steps();
        for (int i = 0; i < steps.size(); i++) {
            SagaStepExecution stepExec = SagaStepExecution.builder()
                    .sagaInstance(saga)
                    .stepName(steps.get(i).name())
                    .stepOrder(i)
                    .status(StepStatus.PENDING)
                    .build();
            saga.getSteps().add(stepExec);
        }

        return saga;
    }

    private SagaStepExecution getOrCreateStepExecution(SagaInstance saga,
                                                        SagaDefinition.StepDefinition<?> stepDef,
                                                        int order) {
        return saga.getSteps().stream()
                .filter(s -> s.getStepOrder() == order)
                .findFirst()
                .orElseGet(() -> {
                    SagaStepExecution stepExec = SagaStepExecution.builder()
                            .sagaInstance(saga)
                            .stepName(stepDef.name())
                            .stepOrder(order)
                            .status(StepStatus.PENDING)
                            .build();
                    saga.getSteps().add(stepExec);
                    return stepExec;
                });
    }

    private String serializeContext(Object context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize saga context", e);
        }
    }

    private <C> C deserializeContext(String json, Class<C> contextType) {
        try {
            return objectMapper.readValue(json, contextType);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize saga context", e);
        }
    }
}
