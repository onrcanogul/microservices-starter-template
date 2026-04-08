package com.template.microservices.example.application.service.saga;

import com.template.messaging.saga.SagaStepHandler;
import com.template.messaging.saga.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saga step: reserve stock for the order.
 * In a real system, this would call an inventory service (REST or via outbox event).
 */
@Slf4j
@Component
public class ReserveStockStep implements SagaStepHandler<CreateOrderSagaContext> {

    @Override
    public StepOutcome<CreateOrderSagaContext> execute(CreateOrderSagaContext context) {
        log.info("Reserving stock: sku={}, amount={}", context.sku(), context.amount());
        // Simulate stock reservation — in production, call inventory service
        CreateOrderSagaContext updated = context.withStockReserved(true);
        return StepOutcome.success("Stock reserved for SKU: " + context.sku(), updated);
    }

    @Override
    public StepResult compensate(CreateOrderSagaContext context) {
        log.info("Releasing stock reservation: sku={}, amount={}", context.sku(), context.amount());
        // Simulate stock release
        return StepResult.success();
    }
}
