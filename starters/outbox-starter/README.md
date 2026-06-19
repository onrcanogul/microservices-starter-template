# outbox-starter

Transactional outbox: persists events in the same DB tx, a ShedLock-guarded scheduler publishes them via `EventPublisher`. Config under `outbox.scheduler.*` and `acme.outbox.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `OutboxStarterAutoConfiguration` | `@EnableScheduling`; component/JPA/entity scan; gated on `outbox.scheduler.enabled` |
| `OutboxService` | `save(destination, event, aggregateType, aggregateId[, correlationId])` — serializes + persists `Outbox` |
| `Outbox` | `@Entity` outbox row (type, destination, payload, published, aggregate, version, correlationId) |
| `OutboxRepository` | `findTop100ByPublishedFalse()`, `deletePublishedBefore(Instant)` |
| `OutboxProcessor` | Publishes top-100 unpublished rows via `EventPublisher`, marks `published=true` |
| `OutboxScheduler` | `run()` at fixed rate + nightly `cleanup()`; `@SchedulerLock` (`outbox_run`, `outbox_cleanup`) |
| `EventClassResolver` | Maps stored `type` string to `Class<? extends Event>` |

## Config
| Property | Default | Meaning |
|----------|---------|---------|
| `outbox.scheduler.enabled` | `true` | Enables the starter |
| `acme.outbox.scheduler.rate` | `1500` | Publish loop fixed-rate (ms) |
| `acme.outbox.cleanup.cron` | `0 0 3 * * *` | Cleanup schedule |
| `acme.outbox.cleanup.retention-days` | `7` | Delete published rows older than N days |

## Depends on
`common-core`, `common-messaging` (`Event`, `MessageHeaders`, `EventVersionUtil`), `persistence-starter` (foundation), `kafka-starter` (`EventPublisher`, foundation), `scheduler-lock-starter` (ShedLock).

## See
`docs/patterns/scheduler-lock.md` · skill `outbox-inbox-pattern`
