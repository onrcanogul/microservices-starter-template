package com.template.messaging.event.stock;

import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: inventory confirmed the reservation.
 * Published by inventory-service, consumed by example-service.
 */
@EventVersion(1)
public record StockReservedEvent(Long orderId, String sku, Integer amount) implements Event {
}
