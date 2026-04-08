# scheduler-lock-starter

Redis-backed distributed scheduler locking via [ShedLock](https://github.com/lukas-krecan/ShedLock). Ensures `@Scheduled` tasks (outbox, inbox, saga) run on **at most one instance** in a cluster.

## Problem

`OutboxScheduler`, `InboxScheduler`, and `SagaScheduler` use Spring's `@Scheduled` to poll periodically. When multiple service instances run behind a load balancer, **every instance** fires the scheduler concurrently — causing duplicate event publishing, redundant saga recovery, and wasted resources.

## Solution

ShedLock intercepts `@Scheduled` methods annotated with `@SchedulerLock` and acquires a Redis-based distributed lock before execution. Only the instance that acquires the lock runs the task; others skip silently.

## How It Works

1. **scheduler-lock-starter** auto-configures a Redis `LockProvider` + enables `@EnableSchedulerLock`
2. **outbox-starter**, **inbox-starter**, **saga-starter** annotate their `@Scheduled` methods with `@SchedulerLock`
3. When `scheduler-lock-starter` is on the classpath → locks are active
4. When `scheduler-lock-starter` is absent → `@SchedulerLock` annotations are inert (tasks run on every instance as before)

## Configuration

```yaml
acme:
  scheduler-lock:
    enabled: true                    # default: true — set false to disable locking
    default-lock-at-most: PT10M      # max lock duration (safety net)
    default-lock-at-least: PT5S      # min lock hold to prevent rapid re-execution
    key-prefix: "shedlock:"          # Redis key prefix for namespace isolation
```

## Lock Names & Durations

| Scheduler | Method | Lock Name | lockAtMostFor | lockAtLeastFor |
|-----------|--------|-----------|---------------|----------------|
| OutboxScheduler | `run()` | `outbox_run` | PT5M | PT1S |
| OutboxScheduler | `cleanup()` | `outbox_cleanup` | PT1H | PT5M |
| InboxScheduler | `process()` | `inbox_process` | PT5M | PT1S |
| InboxScheduler | `cleanup()` | `inbox_cleanup` | PT1H | PT5M |
| SagaScheduler | `detectStuckSagas()` | `saga_detectStuckSagas` | PT10M | PT5S |
| SagaScheduler | `cleanup()` | `saga_cleanup` | PT1H | PT5M |

- **lockAtMostFor**: Safety net — lock is released after this duration even if the holder crashes
- **lockAtLeastFor**: Prevents the same task from immediately running on another instance after fast execution

## Usage

Add to your service POM:

```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>scheduler-lock-starter</artifactId>
</dependency>
```

Requires Redis connection (`spring.data.redis.*` properties). The lock provider uses the same Redis as the cache-starter and API gateway rate limiter.

## Dependencies

- `shedlock-spring` — annotations + Spring integration
- `shedlock-provider-redis-spring` — Redis lock backend
- `spring-boot-starter-data-redis` — Redis connectivity

## Property Namespace

| Prefix | Class |
|--------|-------|
| `acme.scheduler-lock.*` | `SchedulerLockProperties` |
