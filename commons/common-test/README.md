# common-test

Testcontainers base classes that start Postgres and/or Kafka and register their endpoints via `@DynamicPropertySource`. No auto-config.

## Key types
| Type | Role |
|------|------|
| `AbstractPostgresIntegrationTest` | `@Testcontainers`; Postgres container → `spring.datasource.*` |
| `AbstractKafkaIntegrationTest` | `@Testcontainers`; Kafka container → `spring.kafka.bootstrap-servers` |
| `AbstractFullIntegrationTest` | `@Testcontainers`; Postgres + Kafka, both property sets |

Usage: extend a base, add `@SpringBootTest`. Requires Docker.

## Depends on
none (bundles JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers)
