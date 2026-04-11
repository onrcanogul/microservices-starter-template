package com.template.messaging.event.version;

/**
 * Transforms an event payload from one schema version to the next.
 *
 * <p>Implementations are registered as Spring beans and auto-discovered by
 * {@link EventUpcastChain}. Each upcaster handles exactly one version hop
 * (e.g., v1 → v2). The chain composes multiple upcasters for larger jumps.</p>
 *
 * <pre>{@code
 * @Component
 * public class OrderCreatedV1ToV2Upcaster implements EventUpcaster {
 *     public String eventType() { return OrderCreatedEvent.class.getName(); }
 *     public int fromVersion() { return 1; }
 *     public int toVersion()   { return 2; }
 *     public String upcast(String jsonPayload) {
 *         // Add the new 'customerEmail' field with a default
 *         ObjectNode node = (ObjectNode) objectMapper.readTree(jsonPayload);
 *         node.putNull("customerEmail");
 *         return objectMapper.writeValueAsString(node);
 *     }
 * }
 * }</pre>
 */
public interface EventUpcaster {

    /** Fully qualified class name of the event this upcaster handles. */
    String eventType();

    /** Source version this upcaster transforms FROM. */
    int fromVersion();

    /** Target version this upcaster transforms TO (must be {@code fromVersion() + 1}). */
    int toVersion();

    /**
     * Transforms the JSON payload from {@link #fromVersion()} to {@link #toVersion()}.
     *
     * @param jsonPayload the event payload JSON in the source schema
     * @return the transformed JSON in the target schema
     */
    String upcast(String jsonPayload);
}
