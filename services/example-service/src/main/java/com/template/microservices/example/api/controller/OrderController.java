package com.template.microservices.example.api.controller;

import com.template.core.response.ApiResponse;
import com.template.kafka.publisher.EventPublisher;
import com.template.microservices.example.application.service.saga.CreateOrderSagaContext;
import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.starter.idempotency.Idempotent;
import com.template.starter.saga.orchestration.SagaDefinition;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService service;
    private final EventPublisher publisher;
    private final SagaOrchestrator sagaOrchestrator;
    private final SagaDefinition<CreateOrderSagaContext> createOrderSagaDefinition;

    public OrderController(OrderService service,
                           EventPublisher publisher,
                           SagaOrchestrator sagaOrchestrator,
                           SagaDefinition<CreateOrderSagaContext> createOrderSagaDefinition) {
        this.service = service;
        this.publisher = publisher;
        this.sagaOrchestrator = sagaOrchestrator;
        this.createOrderSagaDefinition = createOrderSagaDefinition;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<List<Order>>> get() {
        return ResponseEntity.ok(ApiResponse.ok(service.get()));
    }

    @PostMapping
    @Idempotent
    public void publish(@RequestBody OrderCreatedEvent body) {
        publisher.publish("orders.created", "order.created", body,
                Map.of("x-key", String.valueOf(body.orderId())));
    }

    @PostMapping("/saga")
    @Idempotent(ttlSeconds = 3600)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createOrderViaSaga(@RequestBody OrderCreatedEvent body) {
        CreateOrderSagaContext context = new CreateOrderSagaContext(
                body.orderId(), body.sku(), body.amount(), false, false);
        UUID sagaId = sagaOrchestrator.start(createOrderSagaDefinition, context);
        return ResponseEntity.ok(ApiResponse.ok(sagaId));
    }
}
