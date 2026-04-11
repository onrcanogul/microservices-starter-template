package com.template.microservices.example.api.controller;

import com.template.microservices.example.domain.entity.Order;
import com.template.microservices.example.application.service.order.OrderService;
import com.template.microservices.example.application.service.saga.CreateOrderSagaContext;
import com.template.kafka.publisher.EventPublisher;
import com.template.starter.saga.orchestration.SagaDefinition;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import com.template.starter.security.SecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityAutoConfiguration.class)
@TestPropertySource(properties = "acme.security.jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha256-signing")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private EventPublisher eventPublisher;

    @MockitoBean
    private SagaOrchestrator sagaOrchestrator;

    @MockitoBean
    private SagaDefinition<CreateOrderSagaContext> createOrderSagaDefinition;

    @Test
    @WithMockUser(authorities = "order:read")
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
    @WithMockUser(authorities = "order:read")
    void get_shouldReturnEmptyList() throws Exception {
        when(orderService.get()).thenReturn(List.of());

        mockMvc.perform(get("/api/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createOrderViaSaga_shouldReturnSagaId() throws Exception {
        UUID sagaId = UUID.randomUUID();
        when(sagaOrchestrator.start(any(), any(CreateOrderSagaContext.class))).thenReturn(sagaId);

        mockMvc.perform(post("/api/order/saga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"sku\":\"SKU-001\",\"amount\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(sagaId.toString()));
    }

    @Test
    @WithMockUser(authorities = "other:perm")
    void get_shouldReturn403_whenMissingPermission() throws Exception {
        mockMvc.perform(get("/api/order"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/order"))
                .andExpect(status().isUnauthorized());
    }
}
