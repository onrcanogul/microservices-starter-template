package com.template.microservices.example.application.service.order;

import com.template.microservices.example.domain.entity.Order;

import java.util.List;

public interface OrderService {
    List<Order> get();
    Order getById(Long id);
    Order save(Order model);
    void delete(Long id);

    /** Creates a PENDING order and requests stock reservation (outbox) in one transaction. */
    Order createOrder(String sku, Integer amount);

    /** Compensation: marks the order CANCELLED and requests stock release (outbox) in one transaction. */
    void cancel(Long id);

    /** Reservation succeeded: order -> CONFIRMED. */
    void markConfirmed(Long orderId);

    /** Reservation failed: order -> REJECTED. */
    void markRejected(Long orderId);
}
