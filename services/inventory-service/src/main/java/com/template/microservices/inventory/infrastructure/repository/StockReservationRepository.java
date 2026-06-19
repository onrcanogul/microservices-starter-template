package com.template.microservices.inventory.infrastructure.repository;

import com.template.microservices.inventory.domain.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    Optional<StockReservation> findByOrderId(Long orderId);
}
