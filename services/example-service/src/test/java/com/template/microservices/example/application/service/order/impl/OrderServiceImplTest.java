package com.template.microservices.example.application.service.order.impl;

import com.template.core.exception.BusinessException;
import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository repository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.setId(1L);
        sampleOrder.setSku("SKU-001");
        sampleOrder.setAmount(5);
        sampleOrder.setCreatedAt(Instant.now());
        sampleOrder.setCreatedBy("test-user");
    }

    @Test
    void get_shouldReturnAllOrders() {
        when(repository.findAll()).thenReturn(List.of(sampleOrder));

        List<Order> result = orderService.get();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSku()).isEqualTo("SKU-001");
        verify(repository).findAll();
    }

    @Test
    void get_shouldReturnEmptyList_whenNoOrders() {
        when(repository.findAll()).thenReturn(List.of());

        List<Order> result = orderService.get();

        assertThat(result).isEmpty();
    }

    @Test
    void getById_shouldReturnOrder_whenExists() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        Order result = orderService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSku()).isEqualTo("SKU-001");
    }

    @Test
    void getById_shouldThrowBusinessException_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order not found with id: 99");
    }

    @Test
    void save_shouldPersistAndReturnOrder() {
        when(repository.save(any(Order.class))).thenReturn(sampleOrder);

        Order result = orderService.save(sampleOrder);

        assertThat(result.getId()).isEqualTo(1L);
        verify(repository).save(sampleOrder);
    }

    @Test
    void delete_shouldSoftDeleteOrder() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        when(repository.save(any(Order.class))).thenReturn(sampleOrder);

        orderService.delete(1L);

        assertThat(sampleOrder.isDeleted()).isTrue();
        verify(repository).save(sampleOrder);
    }

    @Test
    void delete_shouldThrowBusinessException_whenOrderNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.delete(99L))
                .isInstanceOf(BusinessException.class);
    }
}
