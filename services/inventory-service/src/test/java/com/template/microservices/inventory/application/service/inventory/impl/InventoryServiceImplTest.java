package com.template.microservices.inventory.application.service.inventory.impl;

import com.template.core.exception.BusinessException;
import com.template.microservices.inventory.domain.entity.Stock;
import com.template.microservices.inventory.domain.entity.StockReservation;
import com.template.microservices.inventory.infrastructure.repository.StockRepository;
import com.template.microservices.inventory.infrastructure.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Stock stock(String sku, int available, int reserved) {
        Stock s = new Stock();
        s.setId(1L);
        s.setSku(sku);
        s.setAvailable(available);
        s.setReserved(reserved);
        return s;
    }

    private StockReservation reservation(Long orderId, String sku, int amount, String status) {
        StockReservation r = new StockReservation();
        r.setId(1L);
        r.setOrderId(orderId);
        r.setSku(sku);
        r.setAmount(amount);
        r.setStatus(status);
        return r;
    }

    @Test
    void reserve_shouldDecrementAvailableAndPersistReservation_whenEnoughStock() {
        Stock s = stock("SKU-001", 10, 0);
        when(reservationRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(stockRepository.findBySku("SKU-001")).thenReturn(Optional.of(s));

        boolean result = inventoryService.reserve(1L, "SKU-001", 4);

        assertThat(result).isTrue();
        assertThat(s.getAvailable()).isEqualTo(6);
        assertThat(s.getReserved()).isEqualTo(4);
        verify(stockRepository).save(s);
        verify(reservationRepository).save(any(StockReservation.class));
    }

    @Test
    void reserve_shouldReturnFalse_whenInsufficientStock() {
        Stock s = stock("SKU-001", 2, 0);
        when(reservationRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(stockRepository.findBySku("SKU-001")).thenReturn(Optional.of(s));

        boolean result = inventoryService.reserve(1L, "SKU-001", 5);

        assertThat(result).isFalse();
        assertThat(s.getAvailable()).isEqualTo(2);
        assertThat(s.getReserved()).isEqualTo(0);
        verify(reservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void reserve_shouldReturnFalse_whenSkuUnknown() {
        when(reservationRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(stockRepository.findBySku("SKU-X")).thenReturn(Optional.empty());

        boolean result = inventoryService.reserve(1L, "SKU-X", 1);

        assertThat(result).isFalse();
        verify(reservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void reserve_shouldBeIdempotent_whenReservationAlreadyExists() {
        when(reservationRepository.findByOrderId(1L))
                .thenReturn(Optional.of(reservation(1L, "SKU-001", 4, "RESERVED")));

        boolean result = inventoryService.reserve(1L, "SKU-001", 4);

        assertThat(result).isTrue();
        verify(stockRepository, never()).findBySku(any());
        verify(stockRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void release_shouldRestoreStockAndMarkReleased() {
        StockReservation r = reservation(1L, "SKU-001", 4, "RESERVED");
        Stock s = stock("SKU-001", 6, 4);
        when(reservationRepository.findByOrderId(1L)).thenReturn(Optional.of(r));
        when(stockRepository.findBySku("SKU-001")).thenReturn(Optional.of(s));

        inventoryService.release(1L);

        assertThat(s.getAvailable()).isEqualTo(10);
        assertThat(s.getReserved()).isEqualTo(0);
        assertThat(r.getStatus()).isEqualTo("RELEASED");
        verify(stockRepository).save(s);
        verify(reservationRepository).save(r);
    }

    @Test
    void release_shouldBeNoOp_whenAlreadyReleased() {
        StockReservation r = reservation(1L, "SKU-001", 4, "RELEASED");
        when(reservationRepository.findByOrderId(1L)).thenReturn(Optional.of(r));

        inventoryService.release(1L);

        verify(stockRepository, never()).findBySku(any());
        verify(stockRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void release_shouldBeNoOp_whenNoReservation() {
        when(reservationRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        inventoryService.release(99L);

        verify(stockRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void getBySku_shouldReturnStock_whenExists() {
        Stock s = stock("SKU-001", 10, 0);
        when(stockRepository.findBySku("SKU-001")).thenReturn(Optional.of(s));

        assertThat(inventoryService.getBySku("SKU-001").getSku()).isEqualTo("SKU-001");
    }

    @Test
    void getBySku_shouldThrowBusinessException_whenNotFound() {
        when(stockRepository.findBySku("SKU-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getBySku("SKU-X"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock not found for sku: SKU-X");
    }
}
