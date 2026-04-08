---
name: outbox-inbox-pattern
description: "Use when implementing transactional outbox or idempotent inbox patterns, adding event publishing to a service transaction, creating InboxProcessor implementations, or adding outbox/inbox Flyway migrations. Covers the full lifecycle: OutboxService.save() in business TX, OutboxProcessor polling, EventClassResolver, InboxService dedup, and InboxProcessor extension."
---

# Outbox & Inbox Patterns

This project guarantees reliable event delivery through two complementary patterns: **transactional outbox** (never lose events) and **idempotent inbox** (never process duplicates). Both are provided as auto-configuration starters.

## Transactional Outbox Flow

```
Business logic + OutboxService.save()  →  [same DB transaction]
          ↓
OutboxScheduler (polls every 1.5s)  →  OutboxProcessor  →  EventPublisher  →  Kafka
          ↓
Mark outbox row as published
```

Always call `OutboxService.save()` inside the same transaction as your business data write. This ensures atomicity — if the business write fails, the event is never created.

### Writing to the Outbox

In a Producer implementation:

```java
@Service
public class OrderCreatedProducer implements Producer<OrderCreatedEvent> {
    private final OutboxService outboxService;

    public OrderCreatedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(OrderCreatedEvent event) {
        outboxService.save(
            "order.created",          // destination (Kafka topic)
            event,                     // event (must implement Event interface)
            Order.class,               // aggregate type
            event.orderId().toString() // aggregate ID
        );
    }
}
```

Call this producer from your service method where the business transaction is active:

```java
@Transactional
public Order createOrder(CreateOrderRequest request) {
    Order order = repository.save(mapToEntity(request));
    orderCreatedProducer.process(new OrderCreatedEvent(order.getId(), order.getSku(), order.getAmount()));
    return order; // both writes in same TX
}
```

### Outbox Entity Schema

The outbox table stores events as JSON text with metadata for routing:

| Column | Type | Purpose |
|--------|------|---------|
| id | UUID (auto) | Primary key |
| type | VARCHAR(512) | Event FQCN (e.g., `com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent`) |
| payload | TEXT | JSON-serialized event |
| destination | VARCHAR(255) | Kafka topic name |
| is_published | BOOLEAN | Tracks publishing state |
| aggregatetype | VARCHAR(255) | Aggregate root class name |
| aggregateid | VARCHAR(255) | Aggregate instance ID |
| created_at | TIMESTAMPTZ | Creation timestamp |

### OutboxProcessor Internals

- Polls `findTop100ByPublishedFalse()` — batches of 100 per cycle
- Uses `EventClassResolver` to deserialize the JSON payload back to the event class
- Publishes via `EventPublisher.publish()` with a 5-second timeout
- Marks row as `published = true` on success
- On failure: logs error, leaves row unpublished for next cycle (automatic retry)

### EventClassResolver Security

`EventClassResolver` prevents arbitrary class loading via an `ALLOWED_PACKAGES` whitelist (default: `com.template`). If your event classes live in a different package, register them explicitly:

```java
@PostConstruct
public void registerEvents() {
    eventClassResolver.register(
        MyEvent.class.getTypeName(),
        MyEvent.class
    );
}
```

### Outbox Configuration

The `enabled` flag has **no `acme.` prefix** (legacy inconsistency) while other properties use `acme.outbox.*`:

```yaml
# ⚠️ enabled flag has NO acme. prefix
outbox:
  scheduler:
    enabled: true             # disable to pause publishing

acme:
  outbox:
    scheduler:
      rate: 1500             # polling interval in ms
    cleanup:
      cron: "0 0 3 * * *"   # daily at 3 AM
      retention-days: 7      # keep published records for 7 days
```

## Idempotent Inbox Flow

```
Kafka Consumer  →  InboxService.save(wrapper)  →  [dedup by UUID]
          ↓
InboxScheduler (polls every 1.5s)  →  InboxProcessor.process()  →  Business logic
          ↓
Mark inbox row as processed
```

### Consumer → Inbox

Consumers **do not process events directly**. They persist to the inbox table:

```java
@Component
public class OrderConsumer implements Consumer<OrderCreatedEvent> {
    private final InboxService inboxService;

    public OrderConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "orders.created", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<OrderCreatedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
```

### InboxService Deduplication

`InboxService.save()` uses a two-layer dedup strategy:
1. **Check first**: `findByIdempotentToken(wrapper.id())` — returns early if token exists
2. **Catch constraint violation**: `DataIntegrityViolationException` caught as fallback for race conditions

The `idempotent_token` column (UUID) is the primary key, so duplicates are structurally impossible.

### Implementing InboxProcessor

Extend the abstract `InboxProcessor` class. Dispatch by event type string:

```java
@Service
public class ExampleInboxProcessor extends InboxProcessor {
    private final InboxRepository inboxRepository;
    private final OrderService orderService;

    public ExampleInboxProcessor(InboxRepository inboxRepository,
                                  ObjectMapper objectMapper,
                                  OrderService orderService) {
        super(objectMapper);  // required — parent uses it for deserialization
        this.inboxRepository = inboxRepository;
        this.orderService = orderService;
    }

    @Override
    @Transactional
    public void process() {
        List<Inbox> pending = inboxRepository.findByProcessedFalse();
        for (Inbox inbox : pending) {
            if (Objects.equals(inbox.getType(), OrderCreatedEvent.class.getName())) {
                OrderCreatedEvent event = getType(inbox.getPayload(), OrderCreatedEvent.class);
                // handle order created event via service layer
            } else if (Objects.equals(inbox.getType(), PaymentFailedEvent.class.getName())) {
                PaymentFailedEvent event = getType(inbox.getPayload(), PaymentFailedEvent.class);
                orderService.delete(event.orderId());
            }
            inbox.setProcessed(true);
            inboxRepository.save(inbox);
        }
    }
}
```

- Mark `@Transactional` on process() to ensure business logic + inbox marking are atomic
- Use `getType(payload, Class)` — the parent's deserialization helper
- Compare types using `EventClass.class.getName()` (fully qualified class name)
- Always mark `setProcessed(true)` after handling, even for unknown types

### Inbox Configuration

```yaml
acme:
  inbox:
    scheduler:
      enabled: true
      rate: 1500
    cleanup:
      cron: "0 0 3 * * *"
      retention-days: 7
```

## Required Flyway Migrations

Every service using outbox/inbox needs these migration files:

**Outbox table** (`V*__create_outbox_table.sql`):
```sql
CREATE TABLE outbox (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregatetype   VARCHAR(255),
    aggregateid     VARCHAR(255),
    type            VARCHAR(512)    NOT NULL,
    payload         TEXT            NOT NULL,
    destination     VARCHAR(255)    NOT NULL,
    is_published    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_is_published ON outbox (is_published) WHERE is_published = FALSE;
```

**Inbox table** (`V*__create_inbox_table.sql`):
```sql
CREATE TABLE inbox (
    idempotent_token    UUID            PRIMARY KEY,
    type                VARCHAR(512)    NOT NULL,
    payload             TEXT            NOT NULL,
    is_processed        BOOLEAN         NOT NULL DEFAULT FALSE,
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX ix_inbox_is_processed ON inbox (is_processed) WHERE is_processed = FALSE;
```

Both use **partial indexes** (WHERE clause) to keep polling queries fast as the tables grow.

## Auto-Configuration Details

Both starters use `@ComponentScan(basePackageClasses = *StarterMarker.class)` for scanning. They are enabled by default (`matchIfMissing = true`). Disable via:

```yaml
outbox:
  scheduler:
    enabled: false    # no acme. prefix for outbox

acme:
  inbox:
    scheduler:
      enabled: false
```

outbox-starter requires `persistence-starter` (JPA) and `kafka-starter` (EventPublisher) on the classpath. inbox-starter requires `persistence-starter` and `common-messaging` only — it does not depend on kafka-starter.
