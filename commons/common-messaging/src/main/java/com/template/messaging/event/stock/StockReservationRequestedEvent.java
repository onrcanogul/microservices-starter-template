package com.template.messaging.event.stock;

import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: order-side asks inventory to reserve stock.
 * Published by example-service, consumed by inventory-service.
 */
@EventVersion(1)
public record StockReservationRequestedEvent(Long orderId, String sku, Integer amount) implements Event {
}
