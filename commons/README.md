# commons

Lowest layer: shared, dependency-light contracts. No business logic. Must not depend on starters/services/infra. No Spring auto-config except `common-web`.

| Module | Purpose |
|--------|---------|
| [common-core](common-core/README.md) | API responses, error codes, business exception, auditing markers, tracing, utils |
| [common-messaging](common-messaging/README.md) | `Event`/`EventWrapper`, producer/consumer, event versioning + upcasting, saga abstractions |
| [common-web](common-web/README.md) | Global exception handler + props (ships the one commons auto-config) |
| [common-test](common-test/README.md) | Testcontainers base classes (Postgres / Kafka) |
