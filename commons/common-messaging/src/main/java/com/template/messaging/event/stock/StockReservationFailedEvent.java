package com.template.messaging.event.stock;

import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: inventory could not reserve stock.
 * Published by inventory-service, consumed by example-service (triggers order rejection).
 */
@EventVersion(1)
public record StockReservationFailedEvent(Long orderId, String sku, String reason) implements Event {
}
