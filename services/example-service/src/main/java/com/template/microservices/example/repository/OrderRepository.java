package com.template.microservices.example.repository;

import com.template.microservices.example.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
