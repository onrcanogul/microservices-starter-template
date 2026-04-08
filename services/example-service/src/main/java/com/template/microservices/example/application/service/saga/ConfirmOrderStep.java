package com.template.microservices.example.application.service.saga;

import com.template.messaging.saga.SagaStepHandler;
import com.template.messaging.saga.StepResult;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.infrastructure.messaging.processor.OrderCreatedProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saga step: confirm the order and publish OrderCreatedEvent via outbox.
 * This is the final step — publishes the domain event after all preconditions are met.
 */
@Slf4j
@Component
public class ConfirmOrderStep implements SagaStepHandler<CreateOrderSagaContext> {

    private final OrderCreatedProducer orderCreatedProducer;

    public ConfirmOrderStep(OrderCreatedProducer orderCreatedProducer) {
        this.orderCreatedProducer = orderCreatedProducer;
    }

    @Override
    public StepOutcome<CreateOrderSagaContext> execute(CreateOrderSagaContext context) {
        log.info("Confirming order: orderId={}", context.orderId());
        orderCreatedProducer.process(
                new OrderCreatedEvent(context.orderId(), context.sku(), context.amount()));
        return StepOutcome.success("Order confirmed and event published", context);
    }

    @Override
    public StepResult compensate(CreateOrderSagaContext context) {
        log.info("Cancelling order confirmation: orderId={}", context.orderId());
        // In production: mark order as cancelled, publish OrderCancelledEvent
        return StepResult.success();
    }
}
