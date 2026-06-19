# inbox-starter

Transactional inbox: idempotent persistence of inbound `EventWrapper`s, ShedLock-guarded processing, and version-aware upcasting. Config under `acme.inbox.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `InboxAutoConfiguration` | `@EnableScheduling`; component/JPA/entity scan; gated on `acme.inbox.scheduler.enabled`; provides `EventUpcastChain` |
| `InboxService` | `save(EventWrapper<? extends Event>)` — dedupes by `idempotentToken`, persists `Inbox` |
| `Inbox` | `@Entity` inbox row (type, payload, processed, idempotentToken, version) |
| `InboxRepository` | `findByProcessedFalse()`, `findByIdempotentToken(UUID)`, `deleteProcessedBefore(Instant)` |
| `InboxProcessor` | Abstract base; `process()` to implement; `getType(payload, eventType[, storedVersion])` deserializes + upcasts |
| `InboxScheduler` | `process()` at fixed rate + nightly `cleanup()`; `@SchedulerLock` (`inbox_process`, `inbox_cleanup`) |
| `EventUpcastChain` | Built from `EventUpcaster` beans; transforms old payloads to current schema |

## Config (`acme.inbox.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `scheduler.enabled` | `true` | Enables the starter |
| `scheduler.rate` | `1500` | Process loop fixed-rate (ms) |
| `cleanup.cron` | `0 0 3 * * *` | Cleanup schedule |
| `cleanup.retention-days` | `7` | Delete processed rows older than N days |

## Depends on
`common-messaging` (`Event`, `EventWrapper`, `MessageHeaders`, `EventUpcaster`/`EventUpcastChain`, `EventVersionUtil`), `persistence-starter` (foundation), `scheduler-lock-starter` (ShedLock).

## See
`docs/patterns/event-versioning.md` · `docs/patterns/scheduler-lock.md` · skill `outbox-inbox-pattern`
