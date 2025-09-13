# Inbox Starter

### Purpose
The **Inbox Starter** provides the foundational building blocks for implementing the **Inbox Pattern** in event-driven microservices.  
It ensures **idempotent event consumption** by recording processed messages in an `inbox` table.

Unlike the Outbox (producer-side reliability), the Inbox guarantees **consumer-side reliability**, making sure the same event is **not applied twice**.

---

### How It Works
There are **two common approaches** to using the Inbox Pattern:

| Approach | How it works | Pros | Cons                                                                                                |
|----------|--------------|------|-----------------------------------------------------------------------------------------------------|
| **Classic Inbox** | Consumer checks the inbox table **immediately** when the event arrives. If already exists → skip, else → save & process. | Low latency, immediate processing | Harder retry handling, consumer must handle errors inline but it can handle with dead letter queues |
| **Scheduled Inbox (this starter)** | Consumer saves the event into the **inbox table** first (status = pending). A **scheduled job** later reads unprocessed inbox entries and applies business logic. | Easier retries, decouples consumption from processing, handles spikes in traffic | Adds processing latency (e.g., 5–10 seconds)                                                        |

This starter provides the **entity, repository, and save service**.  
Each service defines its own **InboxProcessor**, typically triggered by a scheduled job.

---

### Components Provided by Starter
The starter ships with:

#### `Inbox` Entity
Represents a stored event to ensure idempotency.

```java
@Entity
@Table(name = "inbox")
public class Inbox {

    @Id
    private UUID idempotentToken; // event ID

    private String type;
    private String payload;
    private boolean processed;
    ...

    private Instant receivedAt = Instant.now();
}
