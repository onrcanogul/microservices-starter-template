# Structured Logging Starter

**Date**: 2026-04-11  
**Status**: Implemented  
**Module**: `starters/logging-starter`

## What Was Done

Created a `logging-starter` that provides structured JSON logging, MDC propagation, and sensitive data masking as a reusable auto-configuration for all microservices.

### Components

| Component | Purpose |
|-----------|---------|
| `LoggingAutoConfiguration` | Registers `MdcFilter` with conditional annotations |
| `LoggingProperties` | Configuration under `acme.logging.*` namespace |
| `MdcFilter` | Servlet filter populating MDC from gateway headers |
| `SensitiveDataMaskingConverter` | Logback converter for plaintext log masking |
| `MaskingJsonGeneratorDecorator` | Jackson JsonGenerator decorator for JSON log masking |
| `logback-spring.xml` | Profile-based console output (plaintext/JSON) |

### Modified

| Component | Change |
|-----------|--------|
| `EventPublisher` (kafka-starter) | Added MDC→Kafka header propagation for `correlationId` and `userId` |
| `example-service` POM | Added `logging-starter` dependency |
| Config server example-service config | Added `acme.logging.*` properties + updated log pattern |

## Why

### Problem
All services use `@Slf4j` with default plaintext logging. At scale with ELK/Loki:
- Plaintext logs are unparseable by log aggregation tools
- No correlation between HTTP requests and downstream Kafka events
- No user context in log entries (who triggered what)
- Sensitive data (passwords, tokens) freely logged

### Approach: Separate Logging Starter
Created a dedicated starter rather than extending observability-starter because:
- **Single Responsibility**: Observability = metrics/traces, Logging = format/MDC/masking
- **Optional adoption**: Services can use observability without JSON logging and vice versa
- **Profile-based**: Dev keeps readable plaintext; prod gets JSON via `json-logging` profile

### Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| **Extend observability-starter** | Mixes concerns (metrics ≠ logging), forces JSON on all users |
| **Spring Boot 3.4+ structured logging** | Spring Boot 3.4 added basic JSON support but lacks MDC enrichment, masking, and Logstash encoder features needed for production ELK |
| **Log4j2 instead of Logback** | Would require excluding default Logback from Spring Boot; Logback is Spring's default and Logstash encoder is battle-tested |
| **Application-level masking (utility method)** | Misses framework logs, requires manual adoption in every log statement, error-prone |
| **Logback TurboFilter for masking** | TurboFilters are called before formatting — can't mask formatted output reliably |

## How It Scales

- **Small**: Zero config. MDC filter auto-activates, dev logs stay plaintext.
- **Medium**: Activate `json-logging` profile. Logs become ELK/Loki-ingestible. Correlation IDs trace requests across services.
- **Large**: Add centralized log shipping (Filebeat/Fluentd). The structured JSON + MDC fields enable aggregation, alerting, and distributed tracing correlation. Kafka MDC propagation bridges sync→async boundaries.

## Testing

- `MdcFilterTest` — 5 tests covering MDC population, clearing, UUID generation, header propagation, exception safety
- `SensitiveDataMaskingConverterTest` — 8 tests covering JSON masking, key=value masking, case-insensitive matching, null/empty handling
- `LoggingAutoConfigurationTest` — 5 tests covering auto-config registration, property disabling, ConditionalOnMissingBean
