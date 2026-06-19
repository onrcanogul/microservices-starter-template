---
name: kafka-event-flow
description: "Use when working with Kafka messaging: defining events, creating topics, producing/consuming EventWrapper messages, configuring DLT/retry, or debugging Kafka deserialization. Covers EventPublisher trace propagation, Consumer/Producer contracts, topicNamer pattern, trusted packages, and DLT configuration."
---

# Kafka Event Flow

Business-critical events always flow through the outbox pattern. Direct publishing via `EventPublisher` is available for fire-and-forget scenarios but is the exception. Consumers always flow through the inbox pattern. This skill covers the messaging layer that connects them.

## EventWrapper Structure

Every Kafka message is an `EventWrapper<T>` record:

```java
public record EventWrapper<T>(
    UUID id,          // unique message ID (used as inbox idempotent token)
    String type,      // event FQCN (e.g., "com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent")
    String source,    // producing service name (from spring.application.name)
    Instant time,     // publication timestamp
    T event,          // the actual event payload
    Map<String, String> headers  // trace ID, routing key, custom headers
) {}
```

Never send raw event objects to Kafka. The wrapper provides envelope metadata for tracing, deduplication, and routing.

## Defining Events

Events are records that implement the `Event` marker interface:

```java
public record OrderCreatedEvent(
    Long orderId,
    String sku,
    Integer amount
) implements Event {}
```

Place event records in `infrastructure/messaging/` within the service module. They must be in a package under `com.template` to pass the `EventClassResolver` whitelist.

## Topic Configuration

Each service declares its topics using the `topicNamer` function bean provided by kafka-starter:

```java
@Configuration
public class TopicConfig {

    @Bean
    NewTopic ordersCreated(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("orders.created");
    }

    @Bean
    NewTopic ordersCreatedDLT(Function<String, NewTopic> topicNamer,
                               KafkaMessagingProperties mp) {
        return topicNamer.apply("orders.created" + mp.getDltSuffix());
    }
}
```

The `topicNamer` creates topics with 3 partitions and replication factor 1 by default. Always create a matching DLT (dead-letter topic) for each business topic.

## Consumer Contract

Consumers implement `Consumer<T>` and delegate to `InboxService`:

```java
@Component
@Slf4j
public class OrderConsumer implements Consumer<OrderCreatedEvent> {
    private final InboxService inboxService;

    public OrderConsumer(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    @KafkaListener(topics = "orders.created",
                    containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<OrderCreatedEvent> wrapper) {
        inboxService.save(wrapper);
    }
}
```

Specify `containerFactory = "kafkaListenerContainerFactory"` explicitly for readability. This is the default name, but making it explicit documents which factory is in use.

## Producer Contract

Producers implement `Producer<T>` and delegate to `OutboxService`:

```java
@Service
public class OrderCreatedProducer implements Producer<OrderCreatedEvent> {
    private final OutboxService outboxService;

    public OrderCreatedProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void process(OrderCreatedEvent event) {
        outboxService.save("order.created", event, Order.class,
                           event.orderId().toString());
    }
}
```

The destination string is the topic name, not a topic bean reference.

## EventPublisher Internals

`EventPublisher` is auto-configured by kafka-starter. When the outbox processor calls it:

1. Auto-injects `source` from `spring.application.name`
2. Auto-attaches trace ID from MDC via `TraceContext.traceId()`
3. Resolves the Kafka message key via: `x-key` header → wrapper `id` → random UUID fallback
4. In the outbox flow, `OutboxProcessor` always sets `x-key` to the event type, so all events of the same type share a partition key
5. Wraps the event in `EventWrapper` and sends via `KafkaTemplate`

The `KafkaTemplate` has observation enabled for distributed tracing integration.

## DLT and Retry Configuration

kafka-starter configures automatic retry with dead-letter:

```yaml
acme:
  messaging:
    kafka:
      max-attempts: 5        # total attempts including first try
      backoff-ms: 200         # fixed delay between retries
      dlt-suffix: ".DLT"      # dead-letter topic suffix
      trusted-packages: "com.template"  # JsonDeserializer trusted packages
```

Retry behavior:
- Uses `FixedBackOff(backoffMs, maxAttempts - 1)` — not exponential
- `BusinessException` is marked **not retryable** — domain errors go straight to DLT
- `DeadLetterPublishingRecoverer` sends failed messages to `<original-topic>.DLT` preserving the partition

## MessageHeaders Constants

Use the constants from `com.template.messaging.constant.MessageHeaders`:

| Constant | Value | Purpose |
|----------|-------|---------|
| `TRACE_ID` | `x-trace-id` | Distributed trace correlation |
| `CORRELATION_ID` | `x-correlation-id` | Saga / workflow correlation |
| `CAUSATION_ID` | `x-causation-id` | Causal chain between events |
| `KEY` | `x-key` | Kafka partition key |

## Trusted Packages

The `JsonDeserializer` only deserializes classes from trusted packages. Default: `com.template`. If your events are in a different base package, configure:

```yaml
acme:
  messaging:
    kafka:
      trusted-packages: "com.template,com.mycompany.events"
```

Multiple packages are comma-separated.

## Direct Publishing (Bypass Outbox)

For fire-and-forget scenarios where you don't need outbox guarantees (e.g., notifications), you can use `EventPublisher` directly:

```java
publisher.publish("notifications.sent", "notification.sent", event,
    Map.of("x-key", userId));
```

This is the exception, not the rule. Prefer the outbox pattern for business-critical events.

## Gotchas

- The consumer `group-id` defaults to `spring.application.name` — each service instance in the same group shares partitions
- `auto-offset-reset: latest` is the default in the example config — new consumer groups skip historical messages
- Producer acks: `all` with idempotence enabled for exactly-once semantics within a partition
- Compression: `zstd` is configured by default in the example service
- Observation is enabled on both producer (`KafkaTemplate`) and consumer (`ContainerProperties`) for end-to-end trace propagation
