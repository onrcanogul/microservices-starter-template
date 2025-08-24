package com.template.microservices.example.controller;

import com.template.core.response.ApiResponse;
import com.template.kafka.publisher.EventPublisher;
import com.template.microservices.example.entity.Order;
import com.template.microservices.example.event.OrderCreatedEvent;
import com.template.microservices.example.service.OrderService;
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
