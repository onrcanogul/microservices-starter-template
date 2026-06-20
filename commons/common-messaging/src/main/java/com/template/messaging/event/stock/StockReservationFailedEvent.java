package com.template.messaging.event.stock;

import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: inventory could not reserve stock.
 * Published by inventory-service, consumed by example-service (triggers order rejection).
 * One of the two {@link StockReservationReply} variants.
 */
@EventVersion(1)
public record StockReservationFailedEvent(Long orderId, String sku, String reason) implements StockReservationReply {
}
