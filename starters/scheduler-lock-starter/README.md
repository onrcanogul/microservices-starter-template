# scheduler-lock-starter

Redis-backed distributed locks for `@Scheduled` via ShedLock. Namespace `acme.scheduler-lock.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `SchedulerLockAutoConfiguration` | `@EnableSchedulerLock`; on `RedisConnectionFactory` + `LockProvider` classpath |
| `LockProvider` | `RedisLockProvider(connectionFactory, spring.application.name, keyPrefix)` |
| `SchedulerLockProperties` | bound config |

`@EnableSchedulerLock` defaults wired from props: `defaultLockAtMostFor=${...default-lock-at-most:PT10M}`, `defaultLockAtLeastFor=${...default-lock-at-least:PT5S}`.

## Config (`acme.scheduler-lock.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | activate locking |
| `default-lock-at-most` | `10m` | max lock hold (crash safety net) |
| `default-lock-at-least` | `5s` | min lock hold |
| `key-prefix` | `shedlock:` | Redis key prefix |

## Depends on
none (Redis + ShedLock)

## See
`docs/patterns/scheduler-lock.md`
