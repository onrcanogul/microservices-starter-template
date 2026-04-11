# Idempotency Starter

HTTP-level idempotency for POST/PUT endpoints using Redis-backed response caching.

## What It Does

Prevents duplicate request processing by caching responses keyed by a client-provided `Idempotency-Key` header. When the same key is seen again, the previously cached response is replayed without re-executing the handler.

## How It Works

1. Client sends a request with `Idempotency-Key: <unique-key>` header
2. If the key exists in Redis → cached response is replayed (status + body)
3. If the key is new → a distributed lock is acquired, the handler executes, and 2xx responses are cached
4. If another request with the same key arrives concurrently → 409 Conflict is returned
5. Non-2xx responses are **never cached**, so the client can retry on failure

## Usage

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>idempotency-starter</artifactId>
</dependency>
```

### 2. Annotate Endpoints

```java
@PostMapping
@Idempotent
public ResponseEntity<ApiResponse<Order>> create(@RequestBody CreateOrderRequest req) {
    // ...
}

@PostMapping("/charge")
@Idempotent(ttlSeconds = 3600) // 1 hour instead of default 24h
public ResponseEntity<ApiResponse<Payment>> charge(@RequestBody ChargeRequest req) {
    // ...
}
```

### 3. Client Sends Idempotency Key

```bash
curl -X POST /api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"sku": "SKU-001", "amount": 5}'
```

## Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `acme.idempotency.enabled` | `true` | Enable/disable the idempotency filter |
| `acme.idempotency.header-name` | `Idempotency-Key` | HTTP header name for the idempotency key |
| `acme.idempotency.key-prefix` | `idempotency:` | Redis key prefix for namespace isolation |
| `acme.idempotency.default-ttl-seconds` | `86400` (24h) | Default TTL for cached responses |
| `acme.idempotency.lock-ttl-seconds` | `30` | TTL for the distributed lock (concurrent request protection) |

## Error Responses

### Missing Header (400)
```json
{
  "success": false,
  "error": {
    "code": "missing_idempotency_key",
    "message": "Idempotency-Key header is required for this endpoint"
  }
}
```

### Concurrent Duplicate (409)
```json
{
  "success": false,
  "error": {
    "code": "concurrent_request",
    "message": "A request with this idempotency key is already being processed"
  }
}
```

## Overriding Beans

All beans use `@ConditionalOnMissingBean`. You can replace any component:

```java
@Bean
public IdempotencyService customIdempotencyService(StringRedisTemplate redis,
                                                    ObjectMapper mapper,
                                                    IdempotencyProperties props) {
    return new MyCustomIdempotencyService(redis, mapper, props);
}
```

## Redis Key Structure

| Key | Value | TTL |
|-----|-------|-----|
| `idempotency:{key}` | JSON `{"status":200,"contentType":"...","body":"..."}` | Configurable (default 24h) |
| `idempotency:lock:{key}` | `"1"` | 30 seconds |

## Requirements

- `spring-boot-starter-data-redis` on classpath (included transitively)
- `spring-boot-starter-web` on classpath (servlet-based)
- Redis server accessible
