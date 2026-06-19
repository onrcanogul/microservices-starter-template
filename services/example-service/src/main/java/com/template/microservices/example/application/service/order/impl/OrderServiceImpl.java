package com.template.microservices.example.application.service.order.impl;

import com.template.core.error.StandardErrorCodes;
import com.template.core.exception.BusinessException;
import com.template.messaging.event.stock.StockReleaseRequestedEvent;
import com.template.messaging.event.stock.StockReservationRequestedEvent;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.infrastructure.messaging.processor.StockReleaseRequestedProducer;
import com.template.microservices.example.infrastructure.messaging.processor.StockReservationRequestedProducer;
import com.template.microservices.example.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository repository;
    private final StockReservationRequestedProducer stockReservationRequestedProducer;
    private final StockReleaseRequestedProducer stockReleaseRequestedProducer;

    public OrderServiceImpl(OrderRepository repository,
                            StockReservationRequestedProducer stockReservationRequestedProducer,
                            StockReleaseRequestedProducer stockReleaseRequestedProducer) {
        this.repository = repository;
        this.stockReservationRequestedProducer = stockReservationRequestedProducer;
        this.stockReleaseRequestedProducer = stockReleaseRequestedProducer;
    }

    @Override
    public List<Order> get() {
        return repository.findAll();
    }

    @Override
    public Order getById(Long id) {
        return repository.findById(id).orElseThrow(() ->
                BusinessException.of(StandardErrorCodes.NOT_FOUND, "Order not found with id: " + id));
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

    @Override
    @Transactional
    public Order createOrder(String sku, Integer amount) {
        Order order = new Order();
        order.setSku(sku);
        order.setAmount(amount);
        order.setStatus("PENDING");
        Order saved = repository.save(order);
        // Same TX as the order write -> the outbox row commits atomically with the order.
        stockReservationRequestedProducer.process(
                new StockReservationRequestedEvent(saved.getId(), sku, amount));
        return saved;
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        Order order = getById(id);
        order.setStatus("CANCELLED");
        repository.save(order);
        stockReleaseRequestedProducer.process(
                new StockReleaseRequestedEvent(order.getId(), order.getSku(), order.getAmount()));
    }

    @Override
    @Transactional
    public void markConfirmed(Long orderId) {
        Order order = getById(orderId);
        order.setStatus("CONFIRMED");
        repository.save(order);
    }

    @Override
    @Transactional
    public void markRejected(Long orderId) {
        Order order = getById(orderId);
        order.setStatus("REJECTED");
        repository.save(order);
    }
}
