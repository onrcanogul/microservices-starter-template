# cache-starter

Redis-backed Spring Cache with JSON serialization. Namespace `acme.cache.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `CacheAutoConfiguration` | `@EnableCaching`; on `RedisConnectionFactory` + `CacheManager` classpath |
| `CacheManager` | `RedisCacheManager`, transaction-aware, `GenericJackson2JsonRedisSerializer` (default typing NON_FINAL, null values disabled) |
| `CacheProperties` | bound config |

## Config (`acme.cache.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | activate auto-config |
| `default-ttl` | `10m` | global cache TTL |
| `ttl-overrides` | `{}` | per-cache TTL map (`cacheName -> Duration`) |
| `key-prefix` | `cache:` | prefix on Redis cache names |
| `use-key-prefix` | `true` | apply prefix; else `disableKeyPrefix()` |
| `max-pool-size` | `8` | in-flight Redis op pool |

## Depends on
none (Redis from `spring-data-redis`)
