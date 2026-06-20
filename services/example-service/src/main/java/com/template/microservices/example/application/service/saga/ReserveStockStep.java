package com.template.microservices.example.application.service.saga;

import com.template.messaging.event.stock.StockReleaseRequestedEvent;
import com.template.messaging.event.stock.StockReservationFailedEvent;
import com.template.messaging.event.stock.StockReservationReply;
import com.template.messaging.event.stock.StockReservationRequestedEvent;
import com.template.messaging.event.stock.StockReservedEvent;
import com.template.messaging.saga.AsyncSagaStepHandler;
import com.template.messaging.saga.StepResult;
import com.template.microservices.example.infrastructure.messaging.processor.StockReleaseRequestedProducer;
import com.template.microservices.example.infrastructure.messaging.processor.StockReservationRequestedProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Async saga step: reserve stock by asking inventory-service over the outbox and SUSPENDING until the
 * reply arrives. This is the orchestrated-async counterpart to the Phase 1 choreography flow — the
 * same stock events feed both. The reply is delivered by {@code ExampleInboxProcessor} via
 * {@code SagaOrchestrator.resumeWithReply}; the engine itself stays Kafka-unaware.
 */
@Slf4j
@Component
public class ReserveStockStep
        implements AsyncSagaStepHandler<CreateOrderSagaContext, StockReservationReply> {

    private final StockReservationRequestedProducer reservationRequestedProducer;
    private final StockReleaseRequestedProducer releaseRequestedProducer;

    public ReserveStockStep(StockReservationRequestedProducer reservationRequestedProducer,
                            StockReleaseRequestedProducer releaseRequestedProducer) {
        this.reservationRequestedProducer = reservationRequestedProducer;
        this.releaseRequestedProducer = releaseRequestedProducer;
    }

    @Override
    public StepOutcome<CreateOrderSagaContext> execute(CreateOrderSagaContext context) {
        log.info("Requesting stock reservation: orderId={}, sku={}, amount={}",
                context.orderId(), context.sku(), context.amount());
        // Publish the request inside this step's transaction (outbox atomicity), then suspend.
        reservationRequestedProducer.process(
                new StockReservationRequestedEvent(context.orderId(), context.sku(), context.amount()));
        return StepOutcome.suspend(String.valueOf(context.orderId()), context);
    }

    @Override
    public StepOutcome<CreateOrderSagaContext> onReply(CreateOrderSagaContext context,
                                                       StockReservationReply reply) {
        return switch (reply) {
            case StockReservedEvent reserved -> {
                log.info("Stock reserved for orderId={}", context.orderId());
                yield StepOutcome.success(context.withStockReserved(true));
            }
            case StockReservationFailedEvent failed -> {
                log.warn("Stock reservation failed for orderId={}: {}", context.orderId(), failed.reason());
                yield StepOutcome.failure(failed.reason());
            }
        };
    }

    @Override
    public Class<StockReservationReply> replyType() {
        return StockReservationReply.class;
    }

    @Override
    public StepResult compensate(CreateOrderSagaContext context) {
        log.info("Compensating: releasing stock reservation for orderId={}", context.orderId());
        // Real cross-service compensation: ask inventory to release what we reserved.
        releaseRequestedProducer.process(
                new StockReleaseRequestedEvent(context.orderId(), context.sku(), context.amount()));
        return StepResult.success();
    }
}
