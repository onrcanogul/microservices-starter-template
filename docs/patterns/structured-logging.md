# Structured Logging

**Decision:** A dedicated `logging-starter` providing structured JSON logging (Logback + Logstash encoder), MDC propagation from gateway headers, and sensitive-data masking. Profile-based: plaintext in dev, JSON via `json-logging` profile in prod.

**Why:**
- Plaintext logs are unparseable by ELK/Loki at scale
- No correlation between HTTP requests and downstream Kafka events
- No user context (who triggered what) in log entries
- Sensitive data (passwords, tokens) freely logged
- Separate from observability-starter: logging (format/MDC/masking) ≠ metrics/traces; allow independent adoption

**Alternatives rejected:**
- Extend observability-starter — mixes concerns, forces JSON on all users
- Spring Boot 3.4+ structured logging — lacks MDC enrichment, masking, Logstash encoder features needed for prod ELK
- Log4j2 — requires excluding Spring's default Logback; Logstash encoder is battle-tested on Logback
- Application-level masking utility — misses framework logs, manual per-statement adoption, error-prone
- Logback TurboFilter masking — runs before formatting, can't mask formatted output reliably

**Trade-offs:**
- Couples to Logback + Logstash encoder
- Masking operates at the JSON-generator/converter layer (not app code) — central but adds a serialization hook
- Kafka MDC propagation needed to bridge sync→async; null context without the starter
- Large scale still needs external log shipping (Filebeat/Fluentd)

**Implementation:** `logging-starter`: `LoggingAutoConfiguration` (registers `MdcFilter`), `LoggingProperties` (`acme.logging.*`), `MdcFilter` (MDC from gateway headers), `SensitiveDataMaskingConverter` (plaintext), `MaskingJsonGeneratorDecorator` (JSON), `logback-spring.xml` (profile-based console). Modified `EventPublisher` (kafka-starter) to propagate `correlationId`/`userId` MDC → Kafka headers.
