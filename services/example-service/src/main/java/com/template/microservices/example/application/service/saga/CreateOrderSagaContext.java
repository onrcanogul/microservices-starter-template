package com.template.microservices.example.application.service.saga;

/**
 * Context object carried across all steps of the CreateOrder saga.
 * Each step can read and mutate this context.
 */
public record CreateOrderSagaContext(
        Long orderId,
        String sku,
        Integer amount,
        boolean stockReserved,
        boolean paymentCharged
) {
    public CreateOrderSagaContext withStockReserved(boolean reserved) {
        return new CreateOrderSagaContext(orderId, sku, amount, reserved, paymentCharged);
    }

    public CreateOrderSagaContext withPaymentCharged(boolean charged) {
        return new CreateOrderSagaContext(orderId, sku, amount, stockReserved, charged);
    }
}
