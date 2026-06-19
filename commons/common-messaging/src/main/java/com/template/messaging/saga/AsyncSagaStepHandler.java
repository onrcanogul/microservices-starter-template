package com.template.messaging.saga;

/**
 * A saga step whose forward action is asynchronous: {@link #execute(Object)} publishes a request
 * (typically via the outbox) and returns {@link StepOutcome#suspend(String, Object)}. The orchestrator
 * then persists {@code WAITING_FOR_REPLY} and releases the calling thread. When the reply arrives, the
 * service/inbox layer calls {@code SagaOrchestrator.resumeWithReply(correlationKey, reply)}, which
 * invokes {@link #onReply}.
 *
 * <p>The engine stays unaware of Kafka: the step body publishes the request, and the reply→resume
 * wiring lives in the service layer.</p>
 *
 * <p><b>Single-level await:</b> {@link #onReply} must return a Success or Failure outcome — never
 * another Suspended.</p>
 *
 * @param <C> saga context type
 * @param <R> reply payload type
 */
public interface AsyncSagaStepHandler<C, R> extends SagaStepHandler<C> {

    /**
     * Continue this step using the awaited reply. Returns a terminal outcome for the step:
     * Success advances the saga to the next step, Failure triggers compensation. Must NOT return
     * a {@link StepResult.Suspended} outcome.
     *
     * @param context the saga context persisted when the step suspended
     * @param reply   the reply payload, of type {@link #replyType()}
     */
    StepOutcome<C> onReply(C context, R reply);

    /**
     * The reply payload type, used to convert the raw reply object into {@code R} before
     * {@link #onReply} is invoked.
     */
    Class<R> replyType();
}
