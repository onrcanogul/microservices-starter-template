package com.template.microservices.inventory.application.service.inventory;

import com.template.microservices.inventory.domain.entity.Stock;

public interface InventoryService {

    /**
     * Attempts to reserve {@code amount} units of {@code sku} for {@code orderId}.
     * Idempotent: a second call for an {@code orderId} that already has a reservation does not
     * decrement stock again.
     *
     * @return {@code true} if the order now holds a reservation (newly created or already existing),
     *         {@code false} if stock was insufficient / the sku is unknown.
     */
    boolean reserve(Long orderId, String sku, Integer amount);

    /**
     * Releases the reservation held by {@code orderId} (compensation). Idempotent and a no-op
     * when there is nothing reserved for the order or it was already released.
     */
    void release(Long orderId);

    /** Reads current stock for a sku (observation / test endpoint). */
    Stock getBySku(String sku);
}
