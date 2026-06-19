package com.template.microservices.inventory.api.controller;

import com.template.microservices.inventory.application.service.inventory.InventoryService;
import com.template.microservices.inventory.domain.entity.Stock;
import com.template.starter.security.SecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import(SecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "acme.security.jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha256-signing",
        "acme.security.jwt.public-paths=/api/stock/**"
})
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void getBySku_shouldReturnStock_andIsPublic() throws Exception {
        Stock stock = new Stock();
        stock.setId(1L);
        stock.setSku("SKU-001");
        stock.setAvailable(100);
        stock.setReserved(0);

        when(inventoryService.getBySku("SKU-001")).thenReturn(stock);

        mockMvc.perform(get("/api/stock/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-001"))
                .andExpect(jsonPath("$.data.available").value(100))
                .andExpect(jsonPath("$.data.reserved").value(0));
    }
}
