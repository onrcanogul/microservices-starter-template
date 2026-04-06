package com.template.microservices.example.api.controller;

import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.kafka.publisher.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private EventPublisher eventPublisher;

    @Test
    void get_shouldReturnOrders() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setSku("SKU-001");
        order.setAmount(5);
        order.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        order.setCreatedBy("test-user");

        when(orderService.get()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data[0].amount").value(5));
    }

    @Test
    void get_shouldReturnEmptyList() throws Exception {
        when(orderService.get()).thenReturn(List.of());

        mockMvc.perform(get("/api/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
