package com.template.messaging.event.stock;

import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: order-side asks inventory to release a prior reservation
 * (compensation when a later step fails). Published by example-service, consumed by inventory-service.
 */
@EventVersion(1)
public record StockReleaseRequestedEvent(Long orderId, String sku, Integer amount) implements Event {
}
