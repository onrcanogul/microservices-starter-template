# Event Versioning with Upcaster Pattern

**Date**: 2026-04-11  
**Status**: Implemented  
**Modules**: common-messaging, outbox-starter, inbox-starter, example-service

## Problem

Kafka events have no versioning mechanism. When `OrderCreatedEvent` changes (adds, removes, or renames a field), consumers break:

- No way to know which schema version produced a message
- Rolling deployments run producer-v2 + consumer-v1 simultaneously
- No migration path for breaking schema changes
- Field renames and type changes cause silent data loss or deserialization failures

## Decision

Implement a **lightweight upcaster pattern** with header-based version tracking:

1. `@EventVersion(N)` annotation declares the current schema version of an event record
2. Version travels via the existing `EventWrapper.headers()` map as `x-event-version`
3. `EventUpcaster` interface handles one-hop JSON transformations (v1→v2, v2→v3)
4. `EventUpcastChain` composes upcasters to handle multi-version jumps automatically
5. `InboxProcessor.getType(payload, eventType, storedVersion)` runs the chain before deserialization

## Alternatives Considered

### Schema Registry (Avro/Protobuf)
- **Pros**: Industry standard, enforces backward/forward compatibility at registry level, rich tooling
- **Cons**: Requires new infrastructure (Confluent/Apicurio container), complete rewrite of Jackson-based serialization, code generation step, heavier complexity
- **Rejected**: Too heavy for a starter template. Adds operational burden without proportional benefit at this scale.

### Versioned Class Names (EventV1, EventV2)
- **Pros**: Type-safe, simple routing
- **Cons**: Class explosion, no automatic migration from old to new, consumers must maintain all versions
- **Rejected**: Doesn't scale. 10 versions × 20 event types = 200 classes.

### EventWrapper Field Addition
- **Pros**: Clean API (version as first-class field)
- **Cons**: Changes the record constructor, breaks all existing EventWrapper JSON in Kafka topics and databases
- **Rejected**: Breaking change to all serialized messages. Header-based approach is fully backward compatible.

## How It Works

### Production Path (Outbox → Kafka)
```
@EventVersion(2)
record OrderCreatedEvent(Long orderId, String sku, Integer amount, String customerEmail) implements Event {}

1. OrderService calls OutboxService.save("order.created", event, Order.class, "123")
2. OutboxService reads @EventVersion(2) → stores version=2 in outbox table
3. OutboxProcessor reads outbox row → puts x-event-version:2 in headers
4. EventPublisher wraps in EventWrapper with headers containing version
```

### Consumption Path (Kafka → Inbox → Upcaster)
```
1. Consumer receives EventWrapper → InboxService extracts x-event-version from headers
2. InboxService stores version in inbox table alongside payload
3. InboxProcessor reads inbox row → calls getType(payload, OrderCreatedEvent.class, storedVersion)
4. If storedVersion < currentVersion: EventUpcastChain chains upcasters
5. Upcasted JSON is deserialized to current OrderCreatedEvent class
```

### Forward Compatibility (New Producer, Old Consumer)
Jackson's `FAIL_ON_UNKNOWN_PROPERTIES = false` (Spring Boot default) handles this automatically. New fields in the JSON are silently ignored by old consumers.

### Backward Compatibility (Old Producer, New Consumer)
1. Old producer sends v1 payload (no `customerEmail`)
2. New consumer sees storedVersion=1, currentVersion=2
3. `OrderCreatedV1ToV2Upcaster` adds `customerEmail: null` to JSON
4. Jackson deserializes the upcasted JSON to v2 record successfully

## Key Design Decisions

### Version in Headers, Not EventWrapper
The version travels as `x-event-version` in the existing `headers` map. This means:
- Zero changes to EventWrapper record (no breaking change)
- Old messages without the header default to version 1
- Fully backward compatible with all existing serialized data

### Upcasters Operate on JSON Strings
Each `EventUpcaster.upcast(String jsonPayload)` takes and returns raw JSON. This:
- Keeps common-messaging free of Jackson dependency
- Lets implementations use any JSON library (Jackson, Gson, manual string ops)
- Is sufficient for the typical 1-2 hop transformation chains

### Chain Validation
`EventUpcastChain` throws `IllegalStateException` on missing intermediate upcasters. If you have v1→v2 and v3→v4 but not v2→v3, attempting to upcast from v1 to v4 fails fast with a clear error message.

### Backward-Compatible InboxProcessor API
`InboxProcessor.getType(payload, eventType)` still works (no version awareness). The new `getType(payload, eventType, storedVersion)` overload adds upcasting. Existing code doesn't break.

## Scaling Characteristics

| Scale | Behavior |
|-------|----------|
| Small (1-10 events) | No upcasters needed; Jackson handles field additions gracefully |
| Medium (10-50 events) | A few upcasters per service for breaking changes |
| Large (50+ events, many services) | Consider migrating to Schema Registry for automated compatibility checking |

## File Changes

### New Files
- `commons/common-messaging/.../event/version/EventVersion.java` — annotation
- `commons/common-messaging/.../event/version/EventVersionUtil.java` — reflection helper
- `commons/common-messaging/.../event/version/EventUpcaster.java` — SPI
- `commons/common-messaging/.../event/version/EventUpcastChain.java` — chain compositor
- `commons/common-messaging/src/test/.../EventUpcastChainTest.java` — 10 tests
- `commons/common-messaging/src/test/.../EventVersionUtilTest.java` — 3 tests
- `services/example-service/.../upcaster/OrderCreatedV1ToV2Upcaster.java` — demo upcaster
- `services/example-service/.../configuration/EventVersioningConfig.java` — chain bean
- `services/example-service/src/main/resources/db/migration/V6__add_event_version_columns.sql`

### Modified Files
- `MessageHeaders.java` — added `EVENT_VERSION` constant
- `Outbox.java` — added `version` column
- `OutboxService.java` — stamps version from `@EventVersion`
- `OutboxProcessor.java` — passes `x-event-version` header
- `Inbox.java` — added `version` column
- `InboxService.java` — extracts version from EventWrapper headers
- `InboxProcessor.java` — added version-aware `getType()` overload with upcaster chain
- `OrderCreatedEvent.java` — evolved to v2 with `customerEmail` field
- `CreateOrderSagaContext.java` — added `customerEmail` field
- `ConfirmOrderStep.java` — passes `customerEmail` to event
- `OrderController.java` — passes `customerEmail` to saga context
- `ExampleInboxProcessor.java` — uses version-aware `getType()` with `EventUpcastChain`

## Required Flyway Migration

Services using outbox/inbox must add migration:
```sql
ALTER TABLE outbox ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE inbox ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
```
