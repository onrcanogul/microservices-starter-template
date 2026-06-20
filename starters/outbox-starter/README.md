# outbox-starter

Transactional outbox: persists events in the same DB tx; a ShedLock-guarded scheduler publishes them via
`EventPublisher`, with **publish retry + dead-letter** (poison-message handling). Config under
`outbox.scheduler.*` and `acme.outbox.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `OutboxStarterAutoConfiguration` | `@EnableScheduling`; component/JPA/entity scan; gated on `outbox.scheduler.enabled`; binds `OutboxProperties` |
| `OutboxService` | `save(destination, event, aggregateType, aggregateId[, correlationId])`; `replay(id)` — reset a dead row |
| `Outbox` | `@Entity` outbox row: type, destination, payload, published, version, correlationId + `attempts`, `lastError`, `dead`, `nextAttemptAt` |
| `OutboxRepository` | `findBatchToPublish(now, pageable)`, `findByDeadTrue()`, `deletePublishedBefore(Instant)` |
| `OutboxProcessor` | Publishes eligible rows via `EventPublisher` (outside the DB tx), then persists the result (published, or retry/dead) per row |
| `OutboxScheduler` | `run()` at fixed rate + nightly `cleanup()`; `@SchedulerLock` (`outbox_run`, `outbox_cleanup`) |
| `EventClassResolver` | Maps stored `type` string to `Class<? extends Event>` |
| `OutboxProperties` | `@ConfigurationProperties("acme.outbox")` |

## Publish retry / dead-letter
Each row is published OUTSIDE a DB transaction (a slow broker doesn't hold DB locks); the outcome is
persisted in a short per-row transaction: success → `published = true`; failure → `attempts++`,
`lastError`, `nextAttemptAt = now + backoff` until `retry.max-attempts`, then `dead = true` (give up,
stay visible). Dead rows: `findByDeadTrue`, `outbox.published`/`outbox.retried`/`outbox.dead` counters (if a `MeterRegistry`
exists), `log.error`. Replay with `OutboxService.replay(id)`.

## Config
| Property | Default | Meaning |
|----------|---------|---------|
| `outbox.scheduler.enabled` | `true` | Enables the starter (legacy un-namespaced key, preserved) |
| `acme.outbox.scheduler.rate` | `1500ms` | Publish loop fixed-rate |
| `acme.outbox.batch-size` | `100` | Max rows per cycle |
| `acme.outbox.publish-timeout` | `5s` | Per-event publish timeout |
| `acme.outbox.cleanup.cron` | `0 0 3 * * *` | Cleanup schedule |
| `acme.outbox.cleanup.retention-days` | `7` | Delete published rows older than N days |
| `acme.outbox.retry.max-attempts` | `5` | Attempts before dead-lettering |
| `acme.outbox.retry.backoff` | `2s` | Base backoff (exponential, capped) |
| `acme.outbox.retry.max-backoff` | `5m` | Backoff cap |

Each service that owns an `outbox` table must add the retry columns via Flyway (see the messaging-retry migration).

## Depends on
`common-core`, `common-messaging`, `persistence-starter` (foundation, `TransactionTemplate`),
`kafka-starter` (`EventPublisher`, foundation), `scheduler-lock-starter` (ShedLock), `micrometer-core` (optional).

## See
`docs/patterns/poison-message-handling.md` · `docs/patterns/scheduler-lock.md` · skill `outbox-inbox-pattern`
