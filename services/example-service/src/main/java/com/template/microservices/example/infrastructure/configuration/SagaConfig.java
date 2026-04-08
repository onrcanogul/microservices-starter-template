package com.template.microservices.example.infrastructure.configuration;

import com.template.microservices.example.application.service.saga.ChargePaymentStep;
import com.template.microservices.example.application.service.saga.ConfirmOrderStep;
import com.template.microservices.example.application.service.saga.CreateOrderSagaContext;
import com.template.microservices.example.application.service.saga.ReserveStockStep;
import com.template.starter.saga.orchestration.SagaDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SagaConfig {

    @Bean
    public SagaDefinition<CreateOrderSagaContext> createOrderSagaDefinition(
            ReserveStockStep reserveStockStep,
            ChargePaymentStep chargePaymentStep,
            ConfirmOrderStep confirmOrderStep) {

        return SagaDefinition
                .builder("CreateOrderSaga", CreateOrderSagaContext.class)
                .step("reserve-stock", reserveStockStep)
                .step("charge-payment", chargePaymentStep)
                .step("confirm-order", confirmOrderStep)
                .build();
    }
}
