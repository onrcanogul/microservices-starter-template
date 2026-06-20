package com.template.messaging.event.stock;

import com.template.messaging.event.base.Event;

/**
 * The two possible replies to a {@link StockReservationRequestedEvent}, modelled as a sealed
 * contract so an async saga step's {@code onReply} can switch exhaustively over them (Java 21).
 * Both remain {@link Event}s and are published/consumed exactly as before.
 */
public sealed interface StockReservationReply extends Event
        permits StockReservedEvent, StockReservationFailedEvent {
}
