package com.template.starter.saga.orchestration;

import com.template.messaging.saga.SagaStepHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link SagaDefinition}.
 * <p>
 * Usage:
 * <pre>{@code
 * SagaDefinition<OrderContext> saga = SagaDefinition
 *     .builder("CreateOrderSaga", OrderContext.class)
 *     .step("reserve-stock", reserveStockHandler)
 *     .step("charge-payment", chargePaymentHandler)
 *     .step("confirm-order", confirmOrderHandler)
 *     .build();
 * }</pre>
 *
 * @param <C> saga context type
 */
public class SagaDefinitionBuilder<C> {

    private final String sagaType;
    private final Class<C> contextType;
    private final List<SagaDefinition.StepDefinition<C>> steps = new ArrayList<>();

    SagaDefinitionBuilder(String sagaType, Class<C> contextType) {
        this.sagaType = sagaType;
        this.contextType = contextType;
    }

    /**
     * Add a step to the saga. Steps execute in the order they are added.
     *
     * @param name    unique name for this step (used for logging and persistence)
     * @param handler the step handler implementing execute + compensate
     * @return this builder
     */
    public SagaDefinitionBuilder<C> step(String name, SagaStepHandler<C> handler) {
        steps.add(new SagaDefinition.StepDefinition<>(name, handler));
        return this;
    }

    /**
     * Build the immutable saga definition.
     *
     * @return the saga definition
     * @throws IllegalStateException if no steps have been added
     */
    public SagaDefinition<C> build() {
        if (steps.isEmpty()) {
            throw new IllegalStateException("Saga definition must have at least one step");
        }
        return new SagaDefinition<>(sagaType, contextType, List.copyOf(steps));
    }
}
