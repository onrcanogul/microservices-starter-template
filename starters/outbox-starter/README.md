# Outbox Starter

### Purpose of the Outbox
The Outbox Starter provides a production-ready implementation of the **Transactional Outbox Pattern**.  
It ensures **reliable** event publishing in distributed systems by storing events in an **Outbox table** within the same transaction as business data.  
Later, these events are safely relayed and published to a messaging system (e.g., Kafka).

This guarantees that no event is lost even if the service crashes after committing the database transaction but before publishing the event.

---

### How It Works
1. **Domain event is created** during a transaction (e.g., `PaymentCreatedEvent`).
2. The event is serialized and stored in the `outbox` table with `published=false`.
3. The `OutboxProcessor` retrieves unpublished events asynchronously.
4. Events are deserialized back into their Java objects and published via the `EventPublisher`.
5. If publishing succeeds → the record is marked as `published=true`.
6. If publishing fails → the failure is logged and retried later.

---

### Outbox Processor
The core logic is handled by the [`OutboxProcessor`](src/main/java/com/template/starter/outbox/processor/OutboxProcessor.java).

```java
@Service
public class OutboxProcessor {
    @Transactional
    public void processAsync() {
        List<Outbox> outboxes = repository.findByPublishedFalse();
        for (Outbox outbox : outboxes) {
            try {
                // Deserialize the payload back into an Event
                Class<?> raw = Class.forName(outbox.getType());
                Event eventObj = objectMapper.readValue(outbox.getPayload(), (Class<? extends Event>) raw);

                // Publish the event. If everything is okey, mark published
                publisher.publish(outbox.getDestination(), outbox.getType(), eventObj, createHeader(outbox))
                         .thenAccept(sr -> markPublishedNow(outbox.getId()))
                         .exceptionally(ex -> { logFailure(outbox, ex); return null; });
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", outbox.getId());
            }
        }
    }
}
