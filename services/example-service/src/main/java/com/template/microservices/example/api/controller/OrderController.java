package com.template.microservices.example.api.controller;

import com.template.core.response.ApiResponse;
import com.template.kafka.publisher.EventPublisher;
import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent;
import com.template.microservices.example.application.service.order.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService service;
    private final EventPublisher publisher;
    public OrderController(OrderService service, EventPublisher publisher) {
        this.service = service;
        this.publisher = publisher;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Order>>> get() {
        return ResponseEntity.ok(ApiResponse.ok(service.get()));
    }

    @PostMapping
    public void publish(@RequestBody OrderCreatedEvent body) {
        publisher.publish("orders.created", "order.created", body,
                Map.of("x-key", String.valueOf(body.orderId())));
    }
}
