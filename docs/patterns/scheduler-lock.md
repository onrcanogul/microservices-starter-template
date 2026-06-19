# Distributed Scheduler Locking

**Decision:** Use ShedLock with a Redis `LockProvider` to guarantee at-most-once execution of `@Scheduled` tasks across instances. New `scheduler-lock-starter` auto-configures it; outbox/inbox/saga schedulers carry `@SchedulerLock`.

**Why:**
- Spring `@Scheduled` fires on every instance under horizontal scaling
- Outbox: parallel polling → duplicate Kafka events
- Inbox: parallel processing → race conditions
- Saga: parallel stuck-saga detection → concurrent compensation
- A distributed lock lets only one instance run each task per tick

**Alternatives rejected:**
- PostgreSQL advisory locks — ties to PG, connection-scoped lock released early on pool recycle, poor fit for short periodic polls
- Spring Integration leader election — pulls 20+ jars, leader runs ALL schedulers (defeats distribution), needs Zookeeper
- Custom Redis SET NX + TTL — reinvents clock-skew/partition/TTL handling ShedLock already solves
- Quartz + JDBC job store — overkill, 11+ tables, doesn't compose with `@Scheduled`

**Trade-offs:**
- Adds Redis as a dependency for multi-instance correctness
- Lock annotations are inert without the starter on classpath (graceful single-instance degradation)
- `lockAtMostFor` must exceed worst-case task time or a second instance double-runs
- Multi-region needs Redis Sentinel/Cluster for HA

**Implementation:** `scheduler-lock-starter`: `@EnableSchedulerLock` + Redis `LockProvider`; `shedlock-spring` added to outbox/inbox/saga starters. Lock names/durations: `outbox_run` PT5M/PT1S, `outbox_cleanup` PT1H/PT5M, `inbox_process` PT5M/PT1S, `inbox_cleanup` PT1H/PT5M, `saga_detectStuckSagas` PT10M/PT5S, `saga_cleanup` PT1H/PT5M. Polling=PT5M, recovery=PT10M, cleanup=PT1H + `lockAtLeastFor` to prevent re-run/thundering herd.
