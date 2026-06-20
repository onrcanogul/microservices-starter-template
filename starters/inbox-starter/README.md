# inbox-starter

Transactional inbox: idempotent persistence of inbound `EventWrapper`s, ShedLock-guarded processing,
version-aware upcasting, and **processing-layer retry + dead-letter** (poison-message handling). Config
under `acme.inbox.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `InboxAutoConfiguration` | `@EnableScheduling`; component/JPA/entity scan; gated on `acme.inbox.scheduler.enabled`; binds `InboxProperties`; provides `EventUpcastChain` + `InboxProcessingSupport` |
| `InboxService` | `save(EventWrapper)` — dedupes by `idempotentToken`; `replay(token)` — reset a dead row to be re-attempted |
| `Inbox` | `@Entity` inbox row: type, payload, processed, version + `attempts`, `lastError`, `dead`, `nextAttemptAt` |
| `InboxRepository` | `findEligible(now, pageable)`, `findByDeadTrue()`, `findByProcessedFalse()`, `findByIdempotentToken`, `deleteProcessedBefore` |
| `InboxProcessor` | Abstract base. Implement `protected void handle(Inbox)`; the `final process()` template owns polling, per-message transactions and retry/dead. `getType(payload, eventType[, version])` deserializes + upcasts |
| `InboxProcessingSupport` | Poll → per-message TX → success/retry/dead policy + optional Micrometer counters |
| `InboxScheduler` | `process()` at fixed rate + nightly `cleanup()`; `@SchedulerLock` (`inbox_process`, `inbox_cleanup`) |
| `InboxProperties` | `@ConfigurationProperties("acme.inbox")` |

## Poison-message handling (processing layer)
Distinct from the Kafka **transport** DLT (which protects deserialization/listener failures). The base
`process()` runs each row's `handle(...)` in its OWN transaction:
- success → `processed = true`, `lastError = null`;
- `BusinessException` (non-retryable, mirrors the transport `addNotRetryableExceptions`) → `dead = true` immediately;
- any other exception → `attempts++`, `lastError` set; `nextAttemptAt = now + backoff` (exponential `backoff·2^(attempts-1)`, capped) until `attempts >= retry.max-attempts`, then `dead = true`.

Dead rows are a queryable DLQ (`findByDeadTrue`) — observe via the `dead`/`last_error` columns, the
`inbox.dead`/`inbox.retried` counters (if a `MeterRegistry` exists), and `log.error`. Replay with
`InboxService.replay(token)` (or the example-service `POST /api/admin/dead/inbox/{token}/replay`).

## Config (`acme.inbox.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `scheduler.enabled` | `true` | Enables the starter |
| `scheduler.rate` | `1500ms` | Process loop fixed-rate (also read by `@Scheduled` via the same key) |
| `batch-size` | `100` | Max rows per cycle |
| `cleanup.cron` | `0 0 3 * * *` | Cleanup schedule |
| `cleanup.retention-days` | `7` | Delete processed rows older than N days |
| `retry.max-attempts` | `5` | Attempts before a message is dead-lettered |
| `retry.backoff` | `500ms` | Base backoff; effective delay = `min(max-backoff, backoff·2^(attempts-1))` |
| `retry.max-backoff` | `5m` | Backoff cap |

Each service that owns an `inbox` table must add the retry columns via Flyway (see the messaging-retry migration).

## Depends on
`common-messaging`, `common-core` (`BusinessException`), `persistence-starter` (foundation, `TransactionTemplate`),
`scheduler-lock-starter` (ShedLock), `micrometer-core` (optional — counters).

## See
`docs/patterns/poison-message-handling.md` · `docs/patterns/event-versioning.md` · `docs/patterns/scheduler-lock.md` · skill `outbox-inbox-pattern`
