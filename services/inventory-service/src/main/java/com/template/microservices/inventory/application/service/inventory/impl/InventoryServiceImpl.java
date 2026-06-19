package com.template.microservices.inventory.application.service.inventory.impl;

import com.template.core.error.StandardErrorCodes;
import com.template.core.exception.BusinessException;
import com.template.microservices.inventory.application.service.inventory.InventoryService;
import com.template.microservices.inventory.domain.entity.Stock;
import com.template.microservices.inventory.domain.entity.StockReservation;
import com.template.microservices.inventory.infrastructure.repository.StockRepository;
import com.template.microservices.inventory.infrastructure.repository.StockReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_RELEASED = "RELEASED";

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;

    public InventoryServiceImpl(StockRepository stockRepository,
                                StockReservationRepository reservationRepository) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional
    public boolean reserve(Long orderId, String sku, Integer amount) {
        // Idempotency: a reservation already exists for this order — replay, treat as success.
        Optional<StockReservation> existing = reservationRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("Reservation already exists for orderId={} (status={}), skipping reserve", orderId, existing.get().getStatus());
            return STATUS_RESERVED.equals(existing.get().getStatus());
        }

        Optional<Stock> stockOpt = stockRepository.findBySku(sku);
        if (stockOpt.isEmpty()) {
            log.warn("Cannot reserve: unknown sku={} for orderId={}", sku, orderId);
            return false;
        }

        Stock stock = stockOpt.get();
        if (stock.getAvailable() < amount) {
            log.warn("Insufficient stock for sku={} orderId={}: available={}, requested={}",
                    sku, orderId, stock.getAvailable(), amount);
            return false;
        }

        stock.setAvailable(stock.getAvailable() - amount);
        stock.setReserved(stock.getReserved() + amount);
        stockRepository.save(stock);

        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setSku(sku);
        reservation.setAmount(amount);
        reservation.setStatus(STATUS_RESERVED);
        reservationRepository.save(reservation);

        log.info("Reserved {} units of sku={} for orderId={} (available now {})", amount, sku, orderId, stock.getAvailable());
        return true;
    }

    @Override
    @Transactional
    public void release(Long orderId) {
        Optional<StockReservation> resOpt = reservationRepository.findByOrderId(orderId);
        if (resOpt.isEmpty()) {
            log.info("Nothing to release for orderId={} (no reservation)", orderId);
            return;
        }

        StockReservation reservation = resOpt.get();
        if (STATUS_RELEASED.equals(reservation.getStatus())) {
            log.info("Reservation for orderId={} already released, skipping", orderId);
            return;
        }

        stockRepository.findBySku(reservation.getSku()).ifPresent(stock -> {
            stock.setReserved(stock.getReserved() - reservation.getAmount());
            stock.setAvailable(stock.getAvailable() + reservation.getAmount());
            stockRepository.save(stock);
        });

        reservation.setStatus(STATUS_RELEASED);
        reservationRepository.save(reservation);
        log.info("Released reservation for orderId={} (sku={}, amount={})",
                orderId, reservation.getSku(), reservation.getAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public Stock getBySku(String sku) {
        return stockRepository.findBySku(sku).orElseThrow(() ->
                BusinessException.of(StandardErrorCodes.NOT_FOUND, "Stock not found for sku: " + sku));
    }
}
