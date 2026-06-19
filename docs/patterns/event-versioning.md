# Event Versioning (Upcaster Pattern)

**Decision:** Lightweight header-based upcaster pattern. `@EventVersion(N)` declares an event's schema version; it travels as `x-event-version` in `EventWrapper.headers()`; `EventUpcaster` does one-hop JSON transforms; `EventUpcastChain` composes them; `InboxProcessor.getType(payload, type, storedVersion)` upcasts before deserialization.

**Why:**
- Kafka events had no versioning — schema changes silently broke consumers
- Rolling deploys run producer-v2 alongside consumer-v1
- Need a migration path for breaking field renames/removals/type changes
- Header-based version is fully backward compatible with existing serialized data
- Forward compat handled by Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` (new fields ignored)

**Alternatives rejected:**
- Schema Registry (Avro/Protobuf) — new infra (Confluent/Apicurio), full serialization rewrite, codegen; too heavy for a starter
- Versioned class names (EventV1/V2) — class explosion (10 versions × 20 events = 200 classes), no auto-migration
- Add version field to EventWrapper — breaks the record constructor and all existing JSON in topics/DB

**Trade-offs:**
- Upcasters operate on raw JSON strings — keeps common-messaging Jackson-free and library-agnostic, but no compile-time type safety in the transform
- Old messages without the header default to version 1
- `EventUpcastChain` fails fast (`IllegalStateException`) on a missing intermediate hop — explicit but requires complete chains
- At 50+ events/many services, consider migrating to a Schema Registry for automated compatibility checks
- Requires Flyway migration adding `version INTEGER NOT NULL DEFAULT 1` to outbox/inbox

**Implementation:** `common-messaging`: `EventVersion`, `EventVersionUtil`, `EventUpcaster`, `EventUpcastChain`, `MessageHeaders.EVENT_VERSION`. `outbox-starter`: `OutboxService` stamps version, `OutboxProcessor` sets `x-event-version` header, `Outbox.version`. `inbox-starter`: `InboxService` extracts version, `Inbox.version`, `InboxProcessor` version-aware `getType()` overload (old overload kept). `example-service`: `OrderCreatedV1ToV2Upcaster`, `EventVersioningConfig`.
