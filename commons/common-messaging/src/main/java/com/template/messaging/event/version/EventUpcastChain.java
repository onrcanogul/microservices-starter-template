package com.template.messaging.event.version;

import java.util.*;

/**
 * Chains multiple {@link EventUpcaster} instances to transform an event payload
 * across multiple schema versions in a single pass.
 *
 * <p>Given upcasters for v1→v2, v2→v3, and v3→v4, calling
 * {@code upcast("MyEvent", 1, 4, payload)} will chain all three transformations.</p>
 *
 * <p>This class has no framework dependencies and is safe to use in common-messaging.
 * Spring-based auto-discovery happens in the inbox/outbox starters.</p>
 */
public final class EventUpcastChain {

    private static final EventUpcastChain EMPTY = new EventUpcastChain(List.of());

    /**
     * Map: eventType FQCN → sorted map of (fromVersion → upcaster).
     */
    private final Map<String, TreeMap<Integer, EventUpcaster>> registry;

    public EventUpcastChain(List<EventUpcaster> upcasters) {
        this.registry = new HashMap<>();
        for (EventUpcaster upcaster : upcasters) {
            if (upcaster.toVersion() != upcaster.fromVersion() + 1) {
                throw new IllegalArgumentException(
                        String.format("Upcaster for '%s' must have toVersion == fromVersion + 1, " +
                                      "but got fromVersion=%d, toVersion=%d",
                                      upcaster.eventType(), upcaster.fromVersion(), upcaster.toVersion()));
            }
            EventUpcaster existing = registry
                    .computeIfAbsent(upcaster.eventType(), k -> new TreeMap<>())
                    .put(upcaster.fromVersion(), upcaster);
            if (existing != null) {
                throw new IllegalArgumentException(
                        String.format("Duplicate upcaster for event '%s' fromVersion=%d. " +
                                      "Only one upcaster per version hop is allowed.",
                                      upcaster.eventType(), upcaster.fromVersion()));
            }
        }
    }

    /**
     * Returns an empty chain that performs no transformations.
     */
    public static EventUpcastChain empty() {
        return EMPTY;
    }

    /**
     * Transforms the event JSON payload from {@code fromVersion} to {@code toVersion}
     * by chaining all registered upcasters in order.
     *
     * @param eventType   the fully qualified event class name
     * @param fromVersion the version the payload was serialized with
     * @param toVersion   the target version (typically the current code version)
     * @param payload     the raw JSON payload
     * @return the transformed payload, or the original if no upcasters apply
     * @throws IllegalStateException if a gap in the upcaster chain is detected
     */
    public String upcast(String eventType, int fromVersion, int toVersion, String payload) {
        if (fromVersion >= toVersion) {
            return payload;
        }

        TreeMap<Integer, EventUpcaster> chain = registry.get(eventType);
        if (chain == null) {
            return payload;
        }

        String result = payload;
        for (int v = fromVersion; v < toVersion; v++) {
            EventUpcaster upcaster = chain.get(v);
            if (upcaster == null) {
                throw new IllegalStateException(
                        String.format("Missing upcaster for event '%s' from version %d to %d. " +
                                      "Register an EventUpcaster bean for this version hop.", eventType, v, v + 1));
            }
            result = upcaster.upcast(result);
        }
        return result;
    }

    /**
     * Returns true if this chain has any upcasters registered for the given event type.
     */
    public boolean hasUpcastersFor(String eventType) {
        return registry.containsKey(eventType);
    }
}
