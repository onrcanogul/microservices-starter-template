package com.template.microservices.example.application.service.saga;

import com.template.messaging.saga.SagaStepHandler;
import com.template.messaging.saga.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saga step: charge payment for the order.
 * In a real system, this would call a payment service.
 */
@Slf4j
@Component
public class ChargePaymentStep implements SagaStepHandler<CreateOrderSagaContext> {

    @Override
    public StepOutcome<CreateOrderSagaContext> execute(CreateOrderSagaContext context) {
        log.info("Charging payment: orderId={}, amount={}", context.orderId(), context.amount());
        // Simulate payment charge — in production, call payment service
        CreateOrderSagaContext updated = context.withPaymentCharged(true);
        return StepOutcome.success("Payment charged for order: " + context.orderId(), updated);
    }

    @Override
    public StepResult compensate(CreateOrderSagaContext context) {
        log.info("Refunding payment: orderId={}, amount={}", context.orderId(), context.amount());
        // Simulate refund
        return StepResult.success();
    }
}
