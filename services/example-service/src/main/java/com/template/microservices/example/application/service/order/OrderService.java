package com.template.microservices.example.application.service.order;

import com.template.microservices.example.domain.entity.Order;

import java.util.List;

public interface OrderService {
    List<Order> get();
    Order getById(Long id);
    Order save(Order model);
    void delete(Long id);
}
