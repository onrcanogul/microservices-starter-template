# idempotency-starter

HTTP idempotency for non-safe methods via Redis-cached responses. Namespace `acme.idempotency.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `IdempotencyAutoConfiguration` | on `StringRedisTemplate` + `RequestMappingHandlerMapping` classpath |
| `IdempotencyFilter` | `OncePerRequestFilter` (order `0`); skips GET/HEAD/OPTIONS/TRACE; replays cache, double-check lock, caches 2xx only |
| `IdempotencyService` | Redis get/store/lock; UUID-owner lock + Lua atomic unlock |
| `@Idempotent` | method annotation; `ttlSeconds()` default `-1` (use global) |
| `CachedResponse` | record `(status, contentType, body, headers)` |
| `IdempotencyProperties` | bound config |

## Config (`acme.idempotency.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | register filter |
| `header-name` | `Idempotency-Key` | request header carrying the key |
| `key-prefix` | `idempotency:` | Redis key prefix |
| `default-ttl-seconds` | `86400` | cached-response TTL |
| `lock-ttl-seconds` | `30` | distributed lock TTL |

Behavior: missing key -> 400 `missing_idempotency_key`; concurrent -> 409 `concurrent_request`. Cache key scoped by `METHOD:URI:key`.

## Depends on
none (Redis + Spring MVC)
