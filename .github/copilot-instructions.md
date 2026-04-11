# Copilot Instructions — Enterprise Microservices Starter Template

## Project Identity

This is an **enterprise-grade microservices starter template** designed for reuse as a foundation for production microservice projects. It must scale from 100 to 1,000,000+ users. Every decision should prioritize **production-readiness, scalability, and operational excellence**.

The primary user is learning while building — always explain **why** a decision is made, not just what. When making changes, create/update a markdown doc under `docs/` explaining the reasoning, trade-offs considered, and alternatives rejected.

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
| Testing | JUnit 5, Testcontainers, MockitoBean | latest |
| Build | Maven (multi-module) | 3.9+ |
| Container | Docker, Docker Compose, Kubernetes | - |
| CI/CD | GitHub Actions | per-service CI + CD |

## Module Architecture

```
root (parent POM — dependency management only)
├── commons/                    # Shared libraries (no auto-config)
│   ├── common-core             # ApiResponse, ErrorCode, BusinessException, audit interfaces, Preconditions, TraceContext
│   ├── common-messaging        # Event interface, EventWrapper, Consumer/Producer contracts, SagaStep/SagaRollback
│   ├── common-web              # GlobalExceptionHandler, WebErrorProperties (supports ApiResponse + ProblemDetail)
│   └── common-test             # Abstract*IntegrationTest base classes (Testcontainers)
├── starters/                   # Spring Boot auto-configuration starters
│   ├── persistence-starter     # Hibernate tuning, JPA auditing, TransactionTemplate
│   ├── kafka-starter           # KafkaTemplate config, EventPublisher with trace propagation
│   ├── outbox-starter          # Transactional outbox pattern (scheduler + processor + cleanup)
│   ├── inbox-starter           # Idempotent inbox pattern (dedup with UUID token)
│   ├── security-starter        # JWT filter chain, SecurityProperties, BCrypt, public-path config
│   └── observability-starter   # Micrometer common tags, URI cardinality guard, trace-id response header
├── services/                   # Business microservices
│   └── example-service         # Reference implementation showing all patterns in action
├── infra/                      # Infrastructure services
│   ├── api-gateway             # Spring Cloud Gateway, JWT validation, rate limiting (Redis), CORS
│   ├── discovery-service       # Eureka Server
│   ├── config-server           # Spring Cloud Config (native, per-service configs)
│   ├── docker/                 # Docker Compose files (dev, test, prod)
│   ├── k8s/                    # Kubernetes manifests
│   └── prometheus/             # Prometheus scrape config
└── scripts/                    # CI/perf scripts (coverage check, Gatling, perf thresholds)
```

## Coding Conventions

### General Rules
- **Java 21**: Use records, sealed interfaces, pattern matching where appropriate. No `var` abuse — use explicit types for readability in enterprise code.
- **Constructor injection only**: Never use `@Autowired` on fields. All dependencies via constructor (Lombok `@RequiredArgsConstructor` is acceptable).
- **Lombok**: Use sparingly — `@Getter`, `@Setter`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`. Never `@Data` on JPA entities.
- **No `@Data` on entities**: JPA entities use `@Getter @Setter` only. Override `equals`/`hashCode` based on business key or ID if needed.
- **Immutable DTOs**: Use Java records for DTOs, events, and value objects.
- **Explicit over implicit**: Prefer explicit bean definitions in auto-configurations. Always use `@ConditionalOnMissingBean` to allow overrides.

### Package Structure for Services
Each service follows a **layered architecture with clean separation**:
```
com.template.microservices.<service-name>/
├── api/
│   └── controller/        # REST controllers — thin, delegates to service layer
├── application/
│   └── service/           # Business logic interfaces + impl/
│       └── inbox/         # Inbox processors (if applicable)
├── domain/
│   └── entity/            # JPA entities, value objects, domain events
├── infrastructure/
│   ├── configuration/     # Spring config classes (topic, bean defs)
│   ├── messaging/
│   │   ├── consumer/      # Kafka consumers
│   │   └── processor/     # Event producers
│   └── repository/        # Spring Data JPA repositories
```

### API Design
- **Always wrap responses** in `ApiResponse<T>` from common-core.
- **Error responses** follow a consistent structure: `{ success: false, error: { code, message, details, traceId } }`.
- Use `ProblemDetail` (RFC 9457) as the alternative format (configurable via `acme.web.error.format`).
- REST endpoints: `GET /api/<resource>`, `POST /api/<resource>`, `PUT /api/<resource>/{id}`, `DELETE /api/<resource>/{id}`.
- Use proper HTTP status codes. Never return 200 for errors.

### Error Handling
- Domain errors → `BusinessException` with an `ErrorCode` (carries HTTP status + machine code).
- Define service-specific error codes by implementing `ErrorCode` interface (see `StandardErrorCodes`).
- The `GlobalExceptionHandler` in common-web handles all mapping. Don't catch exceptions in controllers.
- Validation: Use Jakarta Bean Validation (`@Valid`, `@NotBlank`, etc.) — handler maps `MethodArgumentNotValidException` to structured errors automatically.

### Event-Driven Patterns
- **Outbox Pattern**: Write events to `outbox` table in the same transaction as business data. `OutboxScheduler` polls and publishes via `EventPublisher`. Never publish directly to Kafka in a business transaction.
- **Inbox Pattern**: Consumers write to `inbox` table with idempotent UUID token before processing. Prevents duplicate event handling.
- **EventWrapper<T>**: All Kafka messages are wrapped with `id`, `type`, `source`, `time`, `event`, `headers`. Never send raw payloads.
- **Events implement `Event` interface** from common-messaging.
- **Saga**: Use `SagaStep<P,R>` interface for orchestrated sagas. `@SagaRollback` marks compensating actions.

### Security
- **Gateway-level**: `JwtAuthFilter` validates JWT, extracts `X-User-Id` and `X-User-Email` headers for downstream services.
- **Service-level**: `security-starter` provides `JwtAuthenticationFilter` + `SecurityFilterChain`. Public paths configurable via `acme.security.public-paths`.
- **Never hardcode secrets**: Use `${JWT_SECRET}` env var or Kubernetes secrets. Config server stores non-sensitive configs only.
- CSRF is disabled (stateless API). Session policy: `STATELESS`.

### Persistence
- **Always use Flyway or Liquibase** for schema migrations (db/migration folder).
- Entity auditing: Implement `IInsertAuditing`, `IUpdateAuditing`, `ISoftDelete` from common-core.
- Soft delete: Use `SoftDelete` utility. Never hard-delete user data.
- Batch operations: `persistence-starter` configures `hibernate.jdbc.batch_size`, `order_inserts`, `order_updates`.
- Default timezone: UTC.

### Testing
- **Unit tests**: Pure JUnit 5 + Mockito. Mock external dependencies. Test business logic in isolation.
- **Integration tests**: Extend `AbstractPostgresIntegrationTest`, `AbstractKafkaIntegrationTest`, or `AbstractFullIntegrationTest` from common-test. These provide Testcontainers with `@DynamicPropertySource`.
- **Controller tests**: Use `@WebMvcTest` + `@MockitoBean` (not the deprecated `@MockBean`).
- **Test naming**: `methodName_condition_expectedResult` pattern.
- **application-test.yml**: Each service has a test profile config. Use `spring.config.import=optional:configserver:` for test isolation.

### Auto-Configuration Starters
- Every starter uses `@AutoConfiguration` (not `@Configuration`).
- Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Use `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty` to make starters non-intrusive.
- Properties under `acme.*` namespace (e.g., `acme.persistence.*`, `acme.security.*`, `acme.obs.*`, `acme.outbox.*`).
- Use `@EnableConfigurationProperties` with dedicated `*Properties` classes.

### Infrastructure
- **API Gateway**: Reactive (WebFlux). Rate limiting via Redis (`RequestRateLimiter`). Routes defined declaratively in YAML.
- **Config Server**: Native profile, configs under `src/main/resources/configs/<service-name>/application.yml`.
- **Docker Compose**: 3 profiles — `docker-compose.yml` (dev), `docker-compose.test.yml`, `docker-compose.prod.yml`.
- **Kubernetes**: Manifests in `infra/k8s/`. Secrets via `jwt-secret.yaml` (sealed secrets in production).

### CI/CD
- Each service has separate `<service>-ci.yml` and `<service>-cd.yml` GitHub Actions workflows.
- CI: Build, test, coverage check.
- CD: Docker build + push + deploy.
- Keep pipelines independent per service — microservice autonomy.

## Scalability Principles

1. **Stateless services**: No server-side sessions. JWT carries auth context.
2. **Database per service**: Each microservice owns its data. No shared databases.
3. **Async communication**: Prefer Kafka events over synchronous REST calls between services.
4. **Outbox for reliability**: Never lose events — persist before publish.
5. **Inbox for idempotency**: Handle duplicate messages gracefully.
6. **Rate limiting**: API Gateway protects downstream services.
7. **Circuit breaking**: Use Resilience4j for inter-service calls when synchronous communication is necessary.
8. **Horizontal scaling**: Services are stateless and can be replicated. Kafka partitions enable parallel consumers.
9. **Observability**: Every service exposes metrics (Micrometer), traces (distributed tracing), and structured logs.
10. **Config externalization**: Spring Cloud Config Server enables runtime config changes without redeployment.

## When Creating New Services

1. Copy `example-service` as a template.
2. Update `groupId`, `artifactId`, and `spring.application.name`.
3. Add the service module to the parent `pom.xml` `<modules>`.
4. Create a config file under `infra/config-server/src/main/resources/configs/<service-name>/application.yml`.
5. Add a route in `api-gateway/application.yml`.
6. Create CI/CD workflows from existing templates.
7. Add Docker and K8s manifests.
8. Write tests extending the appropriate `Abstract*IntegrationTest`.

## When Creating New Starters

1. Create under `starters/<starter-name>/`.
2. Use `@AutoConfiguration` with conditional annotations.
3. Create a `*Properties` class under `property/` package.
4. Register auto-configuration in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
5. Provide sensible defaults. Make everything overridable via `@ConditionalOnMissingBean`.
6. Write a `README.md` explaining the starter's purpose, configuration properties, and usage.

## Documentation Rule

**Every significant change MUST include a doc file under `docs/`** explaining:
- What was done
- Why it was done (the reasoning)
- What alternatives were considered and why they were rejected
- How the decision scales from small to large deployments

Format: `docs/YYYY-MM-DD-<topic-slug>.md` (e.g., `docs/2026-04-06-outbox-pattern-design.md`).

## Quality Gates

- No `System.out.println` — use `@Slf4j` + structured logging.
- No raw `try-catch` in controllers — let `GlobalExceptionHandler` handle it.
- No circular dependencies between modules.
- commons → starters → services (dependency flows one direction).
- Starters must not depend on other starters (except commons).
- Services can depend on any number of starters + commons.

## Property Namespace Reference

| Prefix | Module | Purpose |
|--------|--------|---------|
| `acme.persistence.*` | persistence-starter | Hibernate tuning, audit, TX timeout |
| `acme.security.*` | security-starter | JWT secret, expiry, public paths |
| `acme.obs.*` | observability-starter | Common tags, URI cardinality limit |
| `acme.outbox.*` | outbox-starter | Scheduler rate, cleanup cron/retention |
| `acme.kafka.*` | kafka-starter | Source name, producer config |
| `acme.web.error.*` | common-web | Error format (json/problem), include-message |
| `acme.webclient.*` | webclient-starter | RestClient/WebClient timeouts, resilience, header propagation |
| `acme.audit.*` | audit-starter | Entity audit trail, revision table config, modified flags |
