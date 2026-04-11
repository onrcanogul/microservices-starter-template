# logging-starter

Structured JSON logging with MDC propagation and sensitive data masking for Spring Boot services.

## Features

- **JSON Structured Logging** — Logstash Logback Encoder for ELK/Loki-ready JSON log output
- **MDC Propagation Filter** — Automatically populates SLF4J MDC with `userId`, `userEmail`, `correlationId`, `requestMethod`, `requestUri` from HTTP headers
- **Sensitive Data Masking** — Redacts password, token, secret, and other sensitive fields in log messages
- **Profile-Based Activation** — Human-readable logs in dev, JSON in prod (via `json-logging` profile)
- **Kafka MDC Propagation** — Correlation ID and user ID from MDC are propagated to Kafka headers via `EventPublisher`

## Quick Start

Add the dependency to your service POM:

```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>logging-starter</artifactId>
</dependency>
```

That's it. The starter auto-configures:
1. `MdcFilter` — populates MDC from gateway-propagated headers
2. `logback-spring.xml` — profile-based console output (plaintext or JSON)
3. `SensitiveDataMaskingConverter` — masks sensitive fields in plaintext logs
4. `MaskingJsonGeneratorDecorator` — masks sensitive fields in JSON logs

## Configuration

```yaml
acme:
  logging:
    enabled: true                    # Master switch (default: true)
    mdc:
      enabled: true                  # MDC filter (default: true)
      user-id-header: X-User-Id
      user-email-header: X-User-Email
      correlation-id-header: X-Correlation-Id
```

> **Note on masking:** Sensitive field patterns are hardcoded in `SensitiveFields.java` for both plaintext and JSON masking. Services needing custom patterns can subclass `SensitiveDataMaskingConverter` or `MaskingJsonGeneratorDecorator`.
>
> **Note on JSON output:** JSON logging requires `logstash-logback-encoder` on the classpath (it's `<optional>` in the starter POM). Services using JSON logging must add the dependency explicitly. See [Activating JSON Output](#activating-json-output).

## Activating JSON Output

### Step 1: Add the Logstash encoder dependency

Since the encoder is `<optional>` in the starter, services using JSON logging must add it explicitly:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

### Step 2: Activate the Spring profile

```bash
java -jar my-service.jar --spring.profiles.active=json-logging
```

## MDC Fields

| MDC Key | Source | Description |
|---------|--------|-------------|
| `userId` | `X-User-Id` header | User ID from JWT (set by gateway) |
| `userEmail` | `X-User-Email` header | User email from JWT (set by gateway) |
| `correlationId` | `X-Correlation-Id` header | Correlation ID (generated if missing) |
| `requestMethod` | HTTP method | GET, POST, PUT, DELETE |
| `requestUri` | Request URI | /api/orders, /api/users |
| `traceId` | Micrometer (observability-starter) | Distributed trace ID |
| `spanId` | Micrometer (observability-starter) | Current span ID |

## JSON Output Example

```json
{
  "@timestamp": "2026-04-11T10:15:30.123Z",
  "@version": "1",
  "message": "Order created successfully",
  "logger_name": "c.t.m.example.service.OrderService",
  "thread_name": "http-nio-8080-exec-1",
  "level": "INFO",
  "service": "example-service",
  "traceId": "abc123def456",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-42",
  "requestMethod": "POST",
  "requestUri": "/api/orders"
}
```

## Kafka MDC Propagation

When `EventPublisher.publish()` is called within an HTTP request context, `correlationId` and `userId` from MDC are automatically propagated to Kafka message headers (`x-correlation-id`, `x-user-id`). This enables end-to-end traceability across async boundaries.

> **Cross-starter coupling:** `kafka-starter`'s `EventPublisher` reads MDC keys `correlationId` and `userId` set by this starter's `MdcFilter`. These MDC key names are string-based constants — there is no compile-time dependency between the two starters.

## Sensitive Data Masking

Fields matching sensitive patterns are masked in both plaintext and JSON output:

**Before**: `{"password":"secret123","username":"admin"}`  
**After**: `{"password":"***MASKED***","username":"admin"}`

Handles JSON format (`"key":"value"`) and key=value format (`key=value`).
