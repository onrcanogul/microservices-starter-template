package com.template.messaging.event.stock;

import com.template.messaging.event.version.EventVersion;

/**
 * Cross-service integration event: inventory confirmed the reservation.
 * Published by inventory-service, consumed by example-service. One of the two
 * {@link StockReservationReply} variants.
 */
@EventVersion(1)
public record StockReservedEvent(Long orderId, String sku, Integer amount) implements StockReservationReply {
}
