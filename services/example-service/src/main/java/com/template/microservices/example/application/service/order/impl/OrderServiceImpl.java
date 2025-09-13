package com.template.microservices.example.application.service.order.impl;

import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.infrastructure.repository.OrderRepository;
import com.template.microservices.example.application.service.order.OrderService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository repository;

    public OrderServiceImpl(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Order> get() {
        return repository.findAll();
    }

    @Override
    public Order getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new NullPointerException());
    }

    @Override
    public Order save(Order model) {
        return repository.save(model);
    }

    @Override
    public void delete(Long id) {
        Order order = getById(id);
        order.setDeleted(true);
        repository.save(order);
    }
}
