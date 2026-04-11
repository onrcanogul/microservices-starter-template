package com.template.messaging.event.version;

import com.template.messaging.event.base.Event;

/**
 * Reads the {@link EventVersion} annotation from an event class.
 */
public final class EventVersionUtil {
    private EventVersionUtil() {}

    /** Default version when {@link EventVersion} is absent. */
    public static final int DEFAULT_VERSION = 1;

    /**
     * Returns the schema version declared on the event class.
     * Defaults to {@link #DEFAULT_VERSION} if {@link EventVersion} is absent.
     */
    public static int getVersion(Class<? extends Event> eventClass) {
        EventVersion annotation = eventClass.getAnnotation(EventVersion.class);
        return (annotation != null) ? annotation.value() : DEFAULT_VERSION;
    }
}
