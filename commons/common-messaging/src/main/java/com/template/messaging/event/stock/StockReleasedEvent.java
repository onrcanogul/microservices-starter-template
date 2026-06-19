package com.template.messaging.event.stock;

import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: inventory released a prior reservation.
 * Published by inventory-service, optionally consumed by example-service.
 */
@EventVersion(1)
public record StockReleasedEvent(Long orderId, String sku) implements Event {
}
