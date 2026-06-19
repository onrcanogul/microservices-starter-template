package com.template.microservices.inventory.api.controller;

import com.template.core.response.ApiResponse;
import com.template.microservices.inventory.application.service.inventory.InventoryService;
import com.template.microservices.inventory.domain.entity.Stock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoint for observing stock levels (manual testing / smoke checks).
 * {@code /api/stock/**} is registered as a public path in application.yml so it can be curled
 * without a token; the write path (reserve/release) is event-driven, never exposed over HTTP.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final InventoryService service;

    public StockController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<Stock>> getBySku(@PathVariable("sku") String sku) {
        return ResponseEntity.ok(ApiResponse.ok(service.getBySku(sku)));
    }
}
