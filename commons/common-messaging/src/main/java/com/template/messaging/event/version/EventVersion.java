package com.template.messaging.event.version;

import java.lang.annotation.*;

/**
 * Declares the schema version of an {@link com.template.messaging.event.base.Event}.
 * Used by the event versioning infrastructure to detect mismatches between
 * the stored version and the current code version, triggering upcaster chains.
 *
 * <p>If absent, the event is treated as version 1.</p>
 *
 * <pre>{@code
 * @EventVersion(2)
 * public record OrderCreatedEvent(Long orderId, String sku, Integer amount, String customerEmail) implements Event {}
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventVersion {
    int value() default 1;
}
