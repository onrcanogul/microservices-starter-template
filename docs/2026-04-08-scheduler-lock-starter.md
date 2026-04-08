# Distributed Scheduler Locking via ShedLock + Redis

**Date**: 2026-04-08
**Scope**: outbox-starter, inbox-starter, saga-starter, new scheduler-lock-starter

## What Was Done

Added Redis-backed distributed locking to all `@Scheduled` tasks in the template using [ShedLock](https://github.com/lukas-krecan/ShedLock). Created a new `scheduler-lock-starter` module that auto-configures the lock infrastructure.

### Changes

1. **New module: `scheduler-lock-starter`** — Provides `@EnableSchedulerLock` + Redis `LockProvider`
2. **`shedlock-spring` added to outbox/inbox/saga starters** — `@SchedulerLock` annotations on all scheduler methods
3. **`scheduler-lock-starter` wired into example-service** — Reference implementation

### Affected Schedulers

| Scheduler | Lock Name | lockAtMostFor | lockAtLeastFor |
|-----------|-----------|---------------|----------------|
| `OutboxScheduler.run()` | `outbox_run` | PT5M | PT1S |
| `OutboxScheduler.cleanup()` | `outbox_cleanup` | PT1H | PT5M |
| `InboxScheduler.process()` | `inbox_process` | PT5M | PT1S |
| `InboxScheduler.cleanup()` | `inbox_cleanup` | PT1H | PT5M |
| `SagaScheduler.detectStuckSagas()` | `saga_detectStuckSagas` | PT10M | PT5S |
| `SagaScheduler.cleanup()` | `saga_cleanup` | PT1H | PT5M |

## Why

When running multiple instances of a service (horizontal scaling), Spring's `@Scheduled` fires on **every instance** independently. This means:

- **Outbox**: Multiple instances poll the same outbox table → duplicate Kafka events
- **Inbox**: Multiple instances try to process the same inbox records → race conditions
- **Saga**: Multiple instances detect the same stuck sagas → concurrent compensation

A distributed lock ensures only **one instance** runs each scheduler task at a time.

## Alternatives Considered

### 1. Database Advisory Locks (PostgreSQL)

**Rejected.** Ties the solution to PostgreSQL. Works for outbox/inbox (already have DB), but requires a persistent connection for the lock duration. Poor fit for periodic polling where the lock should be short-lived. Also, advisory locks are connection-scoped — if the connection pool recycles the connection, the lock is released prematurely.

### 2. Spring Integration Leader Election

**Rejected.** Pulls in the entire Spring Integration dependency tree (~20+ jars). Designed for leader election (long-lived leader), not per-execution locking. The leader instance would run ALL schedulers, defeating the purpose of distributing work. Also requires either Zookeeper or a custom lock registry.

### 3. Custom Redis `SET NX` with TTL

**Rejected.** Reinvents the wheel. ShedLock already handles edge cases: clock skew, network partitions, automatic TTL-based lock expiry, clean Spring integration. A hand-rolled solution would need to handle all these cases and would lack the annotation-based ergonomics.

### 4. Quartz Scheduler with JDBC Job Store

**Rejected.** Overkill. Quartz is designed for complex job scheduling (cron, triggers, job persistence). Our use case is simple periodic polling. Quartz also requires its own schema (11+ tables), adds significant complexity, and doesn't compose well with Spring's `@Scheduled`.

## How It Scales

- **Single instance**: `@SchedulerLock` annotations are inert if `scheduler-lock-starter` is not on the classpath (graceful degradation)
- **2-10 instances**: Redis lock ensures at-most-once execution per schedule tick with sub-millisecond overhead
- **100+ instances**: Redis handles thousands of lock operations/sec. `lockAtLeastFor` prevents thundering herd after fast task completion
- **Multi-region**: Redis Sentinel or Cluster mode for HA. ShedLock's `lockAtMostFor` provides safety net if Redis leader failover occurs

### Lock Duration Guidelines

- **Polling tasks** (outbox_run, inbox_process): `lockAtMostFor=PT5M` — if processing hangs, another instance can pick up after 5 minutes
- **Recovery tasks** (saga_detectStuckSagas): `lockAtMostFor=PT10M` — saga compensation may take longer
- **Cleanup tasks** (cron): `lockAtMostFor=PT1H`, `lockAtLeastFor=PT5M` — runs once per cron cycle, prevent re-run within 5 minutes
