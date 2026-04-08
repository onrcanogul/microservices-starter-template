---
applyTo: "**"
---

# Enterprise Microservices Starter Template — Coding Instructions

> Java 21 · Spring Boot 3.5.5 · Spring Cloud 2025.0.0 · Maven multi-module

## Project Summary

An **enterprise-grade microservices starter template** designed as a reusable foundation for production microservice projects. Scales from 100 to 1,000,000+ users. Prioritize **production-readiness, scalability, and operational excellence** in every decision.

The primary user is learning while building — always explain **why** a decision is made when making changes, and create/update a markdown doc under `docs/` with reasoning, trade-offs, and rejected alternatives.

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.5 |
| Cloud | Spring Cloud | 2025.0.0 |
| Messaging | Apache Kafka (KRaft, no Zookeeper) | 3.7 |
| Database | PostgreSQL | 16 |
| Cache / Rate Limit | Redis | 7 |
| Service Discovery | Eureka | via Spring Cloud |
| API Gateway | Spring Cloud Gateway (reactive) | via Spring Cloud |
| Config | Spring Cloud Config Server | native profiles |
| Observability | Micrometer + Prometheus + Grafana | via observability-starter |
| Security | JWT (HMAC) + Spring Security | stateless |
| Resilience | Resilience4j | 2.2.0 |
| API Docs | Springdoc OpenAPI | 2.8.6 |
| Testing | JUnit 5, Testcontainers 1.20.4, MockitoBean | latest |
| Build | Maven (multi-module) | 3.9+ |
| Container | Docker, Docker Compose, Kubernetes | - |
| CI/CD | GitHub Actions | per-service CI + CD |

## Maven Coordinates

- **GroupId**: `com.acme.enterprise`
- **Parent artifact**: `enterprise-template`
- **Version**: `0.1.0-SNAPSHOT`

## Base Package Convention

| Module Type | Base Package | Example |
|-------------|-------------|---------|
| Commons | `com.template.<module>` | `com.template.core`, `com.template.messaging`, `com.template.web`, `com.template.test` |
| Starters | `com.template.<module>` or `com.template.starter.<name>` | `com.template.persistence`, `com.template.kafka`, `com.template.starter.outbox`, `com.template.starter.security` |
| Services | `com.template.microservices.<service-name>` | `com.template.microservices.example` |
| Infra | `com.template.<infra-name>` | `com.template.gateway`, `com.template.config`, `com.template.service.discovery` |

## Module Architecture

```
root (parent POM — dependency management only)
├── commons/                        # Shared libraries (no auto-config)
│   ├── common-core                 # ApiResponse, PageResponse, ErrorCode, StandardErrorCodes, BusinessException,
│   │                               #   audit interfaces (IInsertAuditing, IUpdateAuditing, ISoftDelete),
│   │                               #   Preconditions, TraceContext, SoftDelete, Ids
│   ├── common-messaging            # Event interface, EventWrapper, MessageHeaders, Consumer/Producer contracts,
│   │                               #   SagaStep, SagaRollback
│   ├── common-web                  # GlobalExceptionHandler, WebErrorProperties, CommonWebAutoConfiguration
│   │                               #   (supports ApiResponse + ProblemDetail via acme.web.error.format)
│   └── common-test                 # AbstractPostgresIntegrationTest, AbstractKafkaIntegrationTest,
│                                   #   AbstractFullIntegrationTest (Testcontainers base classes)
├── starters/                       # Spring Boot auto-configuration starters
│   ├── persistence-starter         # PersistenceAutoConfiguration, PersistenceProperties, Hibernate tuning, JPA auditing
│   ├── kafka-starter               # KafkaMessagingAutoConfiguration, KafkaMessagingProperties, EventPublisher
│   ├── outbox-starter              # OutboxStarterAutoConfiguration, OutboxScheduler, OutboxProcessor, OutboxService,
│   │                               #   Outbox entity, OutboxRepository, EventClassResolver
│   ├── inbox-starter               # InboxAutoConfiguration, InboxService, InboxScheduler, InboxProcessor,
│   │                               #   Inbox entity, InboxRepository, InboxStarterMarker
│   ├── security-starter            # SecurityAutoConfiguration, JwtAuthenticationFilter, JwtService,
│   │                               #   SecurityProperties, AuthenticatedUser
│   ├── observability-starter       # ObservabilityAutoConfiguration, ObservabilityProperties,
│   │                               #   Micrometer common tags, URI cardinality guard, trace-id response header
│   ├── resilience-starter          # ResilienceAutoConfiguration, ResilienceProperties (Resilience4j integration)
│   └── cache-starter               # CacheAutoConfiguration, CacheProperties (Redis caching)
├── services/                       # Business microservices
│   └── example-service             # Reference implementation: OrderController, OrderService, OrderConsumer,
│                                   #   OrderCreatedProducer, ExampleInboxProcessor, TopicConfig
├── infra/                          # Infrastructure services
│   ├── api-gateway                 # Spring Cloud Gateway: JwtAuthFilter, LoggingFilter, RateLimiterConfig
│   ├── discovery-service           # Eureka Server
│   ├── config-server               # Spring Cloud Config (native, per-service configs under configs/)
│   ├── docker/                     # docker-compose.yml (dev), docker-compose.test.yml, docker-compose.prod.yml
│   ├── k8s/                        # Kubernetes manifests (api-gateway, config-server, discovery-service, example-service, jwt-secret)
│   ├── prometheus/                 # prometheus.yml (scrape config)
│   └── grafana/                    # Grafana provisioning (datasources)
└── scripts/                        # CI/perf scripts (check-coverage.sh, run-gatling.sh, assert-perf-thresholds.sh)
```

## Package Structure for Services

Each service follows a **layered architecture with clean separation**:
```
com.template.microservices.<service-name>/
├── api/
│   └── controller/             # REST controllers — thin, delegates to service layer
├── application/
│   └── service/                # Business logic interfaces + impl/
│       └── inbox/              # Inbox processors (if applicable)
├── domain/
│   └── entity/                 # JPA entities, value objects, domain events
├── infrastructure/
│   ├── configuration/          # Spring config classes (TopicConfig, bean defs)
│   ├── messaging/
│   │   ├── consumer/           # Kafka consumers
│   │   └── processor/          # Event producers (outbox integration)
│   └── repository/             # Spring Data JPA repositories
```

## Coding Conventions

### General Rules
- **Java 21**: Use records, sealed interfaces, pattern matching where appropriate. No `var` abuse — use explicit types for readability.
- **Constructor injection only**: Never `@Autowired` on fields. Use Lombok `@RequiredArgsConstructor`.
- **Lombok**: Use sparingly — `@Getter`, `@Setter`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`. **Never** `@Data` on JPA entities.
- **Immutable DTOs**: Use Java records for DTOs, events, and value objects.
- **Logging**: Always `@Slf4j` — never `System.out.println`.
- **Explicit over implicit**: Prefer explicit bean definitions in auto-configurations. Always use `@ConditionalOnMissingBean`.

### API Design
- **Always wrap responses** in `ApiResponse<T>` from common-core.
- Support `ProblemDetail` (RFC 9457) as alternative format via `acme.web.error.format`.
- REST endpoints: `GET /api/<resource>`, `POST /api/<resource>`, `PUT /api/<resource>/{id}`, `DELETE /api/<resource>/{id}`.
- Use proper HTTP status codes. Never return 200 for errors.

### Error Handling
- Domain errors → `BusinessException` with an `ErrorCode` enum/interface.
- Service-specific error codes implement `ErrorCode` interface (see `StandardErrorCodes`).
- `GlobalExceptionHandler` in common-web handles all mapping — **don't catch exceptions in controllers**.
- Validation: Jakarta Bean Validation (`@Valid`, `@NotBlank`, etc.).

### Event-Driven Patterns
- **Outbox**: Write events to `outbox` table in same transaction. `OutboxScheduler` polls + publishes via `EventPublisher`. Never publish directly to Kafka in a business transaction.
- **Inbox**: Consumers write to `inbox` table with UUID token before processing. Prevents duplicates.
- **EventWrapper<T>**: All Kafka messages wrapped with `id`, `type`, `source`, `time`, `event`, `headers`. Never send raw payloads.
- **Events**: Implement `Event` interface from common-messaging.
- **Saga**: Use `SagaStep<P,R>` for orchestrated sagas. `@SagaRollback` marks compensating actions.

### Security
- Gateway: `JwtAuthFilter` validates JWT, extracts `X-User-Id`/`X-User-Email` headers for downstream.
- Service: `security-starter` provides `JwtAuthenticationFilter` + `SecurityFilterChain`. Public paths via `acme.security.public-paths`.
- Never hardcode secrets — use `${JWT_SECRET}` env var or K8s secrets.
- CSRF disabled, session policy: `STATELESS`.

### Persistence
- Always use Flyway or Liquibase for schema migrations (`db/migration`).
- Entity auditing: implement `IInsertAuditing`, `IUpdateAuditing`, `ISoftDelete`.
- Soft delete via `SoftDelete` utility — never hard-delete user data.
- Default timezone: UTC.

### Testing
- **Unit tests**: JUnit 5 + Mockito. Mock external dependencies.
- **Integration tests**: Extend `AbstractPostgresIntegrationTest`, `AbstractKafkaIntegrationTest`, or `AbstractFullIntegrationTest` from common-test (Testcontainers).
- **Controller tests**: `@WebMvcTest` + `@MockitoBean` (not deprecated `@MockBean`).
- **Naming**: `methodName_condition_expectedResult`.
- **Test config**: `application-test.yml` with `spring.config.import=optional:configserver:`.

### Auto-Configuration Starters
- Every starter uses `@AutoConfiguration` (not `@Configuration`).
- Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Use `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`.
- Properties under `acme.*` namespace with dedicated `*Properties` classes.

## Property Namespace Reference

| Prefix | Module | Properties Class |
|--------|--------|-----------------|
| `acme.persistence.*` | persistence-starter | `PersistenceProperties` |
| `acme.security.*` | security-starter | `SecurityProperties` |
| `acme.obs.*` | observability-starter | `ObservabilityProperties` |
| `acme.outbox.*` | outbox-starter | *(via OutboxStarterAutoConfiguration)* |
| `acme.kafka.*` | kafka-starter | `KafkaMessagingProperties` |
| `acme.cache.*` | cache-starter | `CacheProperties` |
| `acme.resilience.*` | resilience-starter | `ResilienceProperties` |
| `acme.web.error.*` | common-web | `WebErrorProperties` |
| `acme.webclient.*` | webclient-starter | `WebClientProperties` |

## Key Files Reference

| Purpose | File |
|---------|------|
| API response wrapper | `commons/common-core/.../core/response/ApiResponse.java` |
| Pagination wrapper | `commons/common-core/.../core/response/PageResponse.java` |
| Business exception | `commons/common-core/.../core/exception/BusinessException.java` |
| Error codes interface | `commons/common-core/.../core/error/ErrorCode.java` |
| Standard error codes | `commons/common-core/.../core/error/StandardErrorCodes.java` |
| Global exception handler | `commons/common-web/.../web/handler/GlobalExceptionHandler.java` |
| Event interface | `commons/common-messaging/.../messaging/event/base/Event.java` |
| Event wrapper | `commons/common-messaging/.../messaging/wrapper/EventWrapper.java` |
| Kafka event publisher | `starters/kafka-starter/.../kafka/publisher/EventPublisher.java` |
| JWT auth filter (gateway) | `infra/api-gateway/.../gateway/filter/JwtAuthFilter.java` |
| JWT auth filter (service) | `starters/security-starter/.../security/filter/JwtAuthenticationFilter.java` |
| Example service (reference) | `services/example-service/` |

## Quality Gates

- No `System.out.println` — use `@Slf4j` + structured logging.
- No raw `try-catch` in controllers — let `GlobalExceptionHandler` handle it.
- No circular dependencies between modules.
- Dependency flow: `commons → starters → services` (one direction only).
- Starters must not depend on other starters (except commons).
- Services can depend on any number of starters + commons.

## CI/CD Workflows

Each service has separate CI and CD workflows under `.github/workflows/`:
- `<service>-ci.yml` — Build, test, coverage check
- `<service>-cd.yml` — Docker build + push + deploy
- Pipelines are independent per service for microservice autonomy.

## When Creating New Services

1. Copy `example-service` as a template
2. Update `groupId`, `artifactId`, `spring.application.name`
3. Add the service module to parent `pom.xml` `<modules>`
4. Create config under `infra/config-server/src/main/resources/configs/<service-name>/application.yml`
5. Add a route in `infra/api-gateway/src/main/resources/application.yml`
6. Create CI/CD workflows from existing templates
7. Add Docker and K8s manifests
8. Write tests extending appropriate `Abstract*IntegrationTest`

## When Creating New Starters

1. Create under `starters/<starter-name>/`
2. Use `@AutoConfiguration` with conditional annotations
3. Create `*Properties` class under `property/` package
4. Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
5. Provide sensible defaults with `@ConditionalOnMissingBean`
6. Add dependency management entry in root `pom.xml`
7. Write a `README.md` explaining purpose, config properties, and usage

## Documentation Rule

Every significant change **must** include a doc under `docs/` formatted as `docs/YYYY-MM-DD-<topic-slug>.md` explaining: what was done, why, alternatives considered, and how it scales.
