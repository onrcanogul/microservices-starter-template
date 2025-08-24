package com.template.microservices.example.service;

import com.template.microservices.example.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderService {
    List<Order> get();
    Order getById(Long id);
    Order save(Order model);
    void delete(Long id);
}
