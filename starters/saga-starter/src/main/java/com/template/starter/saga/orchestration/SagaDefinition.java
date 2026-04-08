package com.template.starter.saga.orchestration;

import com.template.messaging.saga.SagaStepHandler;

import java.util.List;

/**
 * Immutable definition of a saga — the ordered list of steps that make up a saga workflow.
 * Created via {@link SagaDefinitionBuilder}.
 *
 * @param <C> the saga context type (must be JSON-serializable)
 */
public record SagaDefinition<C>(
        String sagaType,
        Class<C> contextType,
        List<StepDefinition<C>> steps
) {

    /**
     * A single step within a saga definition.
     *
     * @param <C> saga context type
     */
    public record StepDefinition<C>(
            String name,
            SagaStepHandler<C> handler
    ) {}

    /**
     * Start building a new saga definition.
     *
     * @param sagaType    unique identifier for this saga type (e.g., "CreateOrderSaga")
     * @param contextType the Class of the saga context
     * @param <C>         context type
     * @return a new builder
     */
    public static <C> SagaDefinitionBuilder<C> builder(String sagaType, Class<C> contextType) {
        return new SagaDefinitionBuilder<>(sagaType, contextType);
    }
}
