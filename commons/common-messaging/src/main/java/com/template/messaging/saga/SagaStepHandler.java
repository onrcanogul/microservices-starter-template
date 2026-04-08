package com.template.messaging.saga;

/**
 * Handler for a single step in an orchestration-based saga.
 *
 * @param <C> saga context type — a mutable or immutable object carrying data across steps.
 *            Typically a Java record or POJO that is JSON-serializable.
 */
public interface SagaStepHandler<C> {

    /**
     * Execute the forward action of this step.
     *
     * @param context the current saga context (from previous steps or initial context)
     * @return {@link StepOutcome} containing the result and optionally an updated context
     */
    StepOutcome<C> execute(C context);

    /**
     * Compensate (undo) this step. Called in reverse order when a later step fails.
     *
     * @param context the saga context at the time compensation was triggered
     * @return {@link StepResult.Success} if compensation succeeded,
     *         {@link StepResult.Failure} if compensation also failed (saga moves to FAILED)
     */
    StepResult compensate(C context);

    /**
     * Outcome of a saga step execution.
     * Carries both the result (success/failure) and optionally an updated context
     * to pass to subsequent steps.
     *
     * @param <C> saga context type
     */
    record StepOutcome<C>(StepResult result, C updatedContext) {

        public static <C> StepOutcome<C> success(C updatedContext) {
            return new StepOutcome<>(StepResult.success(), updatedContext);
        }

        public static <C> StepOutcome<C> success(String output, C updatedContext) {
            return new StepOutcome<>(StepResult.success(output), updatedContext);
        }

        public static <C> StepOutcome<C> failure(String reason) {
            return new StepOutcome<>(StepResult.failure(reason), null);
        }

        public static <C> StepOutcome<C> failure(String reason, Exception cause) {
            return new StepOutcome<>(StepResult.failure(reason, cause), null);
        }
    }
}
