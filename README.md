# Microservices Starter Template

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-brightgreen?logo=spring)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An **enterprise-grade, production-ready microservices starter template** built with Spring Boot 3.5 and Spring Cloud 2025. Designed as a reusable foundation that scales from **100 to 1,000,000+ users**. Co-developed with **agentic AI** (GitHub Copilot with custom skills, agents, and instructions).

> **Use this template** to bootstrap new microservice projects with proven patterns already wired — messaging, saga orchestration, security, resilience, observability, and CI/CD.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway (:8000)                      │
│              JWT validation · Rate limiting · CORS              │
└───────────┬──────────────────────┬──────────────────────────────┘
            │                      │
    ┌───────▼───────┐      ┌───────▼───────┐
    │ Example Svc   │      │  Your Svc     │    ← Copy & extend
    │   (:8080)     │      │   (:808x)     │
    └───┬───┬───┬───┘      └───┬───┬───┬───┘
        │   │   │              │   │   │
   ┌────┘   │   └────┐   ┌────┘   │   └────┐
   ▼        ▼        ▼   ▼        ▼        ▼
┌──────┐ ┌──────┐ ┌──────────┐ ┌──────┐ ┌──────┐
│Postgres│ │Kafka │ │  Redis   │ │Jaeger│ │Prom+ │
│  :5432 │ │:19092│ │  :6379   │ │:16686│ │Grafana│
└────────┘ └──────┘ └──────────┘ └──────┘ └───────┘

   Discovery Service (Eureka :8761)  ·  Config Server (:8888)
```

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.5 |
| Cloud | Spring Cloud | 2025.0.0 |
| Messaging | Apache Kafka (KRaft) | 3.7 |
| Database | PostgreSQL | 16 |
| Cache / Rate Limit | Redis | 7 |
| Service Discovery | Eureka | via Spring Cloud |
| API Gateway | Spring Cloud Gateway (reactive) | via Spring Cloud |
| Config | Spring Cloud Config Server | native profiles |
| Observability | Micrometer + Prometheus + Grafana + Jaeger | via starter |
| Security | JWT (HMAC) + Spring Security | stateless |
| Resilience | Resilience4j | 2.2.0 |
| API Docs | Springdoc OpenAPI | 2.8.6 |
| Testing | JUnit 5 + Testcontainers | 1.20.4 |
| Build | Maven (multi-module) | 3.9+ |
| Container | Docker Compose + Kubernetes | - |
| CI/CD | GitHub Actions | per-service |

## Module Structure

```
root (parent POM)
├── commons/                        Shared libraries (no auto-config)
│   ├── common-core                 ApiResponse, BusinessException, ErrorCode, audit interfaces
│   ├── common-messaging            Event, EventWrapper, SagaStepHandler, Consumer/Producer contracts
│   ├── common-web                  GlobalExceptionHandler (ApiResponse + ProblemDetail)
│   └── common-test                 Testcontainers base classes for integration tests
│
├── starters/                       Spring Boot auto-configuration starters
│   ├── persistence-starter         Hibernate tuning, JPA auditing, batch config
│   ├── kafka-starter               KafkaTemplate, EventPublisher with trace propagation
│   ├── outbox-starter              Transactional outbox (scheduler + processor + cleanup)
│   ├── inbox-starter               Idempotent inbox (UUID dedup)
│   ├── saga-starter                Saga orchestration engine + choreography support
│   ├── security-starter            JWT filter chain, public-path config, BCrypt
│   ├── resilience-starter          Circuit breaker, retry, bulkhead, time limiter
│   ├── cache-starter               Redis caching with TTL config
│   └── observability-starter       Micrometer common tags, URI cardinality guard, trace-id header
│
├── services/                       Business microservices
│   └── example-service             Reference implementation (all patterns wired)
│
├── infra/                          Infrastructure
│   ├── api-gateway                 Reactive gateway, Redis rate limiting, JWT validation
│   ├── discovery-service           Eureka Server
│   ├── config-server               Native config server
│   ├── docker/                     Docker Compose (dev · test · prod)
│   ├── k8s/                        Kubernetes manifests
│   ├── prometheus/                 Scrape config
│   └── grafana/                    Datasource provisioning
│
├── scripts/                        CI/perf scripts
└── .github/
    ├── workflows/                  Per-service CI + CD pipelines
    ├── instructions/               Copilot coding instructions
    └── skills/                     8 domain-specific Copilot skills
```

## Starters

Each starter is a Spring Boot auto-configuration module under the `acme.*` property namespace. Add a dependency and it just works — all beans are `@ConditionalOnMissingBean` for easy overrides.

| Starter | Namespace | What It Does |
|---------|-----------|-------------|
| `persistence-starter` | `acme.persistence.*` | Hibernate batch tuning, JPA auditing (`@CreatedDate`, `@LastModifiedDate`), configurable TX timeout, open-in-view disabled |
| `kafka-starter` | `acme.kafka.*` | `EventPublisher` with automatic trace-id propagation, trusted-packages config, `EventWrapper<T>` serialization |
| `outbox-starter` | `acme.outbox.*` | Transactional outbox: write events in the same TX as business data, poll-based publish, automatic cleanup |
| `inbox-starter` | `acme.inbox.*` | Idempotent inbox: UUID token dedup prevents duplicate event processing |
| `saga-starter` | `acme.saga.*` | **Orchestration**: `SagaDefinition` DSL, per-step TX commit, crash recovery, auto-compensation. **Choreography**: `@SagaRollback` registry, correlation propagation |
| `security-starter` | `acme.security.*` | JWT authentication filter, `SecurityFilterChain`, configurable public paths, `AuthenticatedUser` context |
| `resilience-starter` | `acme.resilience.*` | Resilience4j circuit breaker, retry, bulkhead, time limiter with sensible defaults |
| `cache-starter` | `acme.cache.*` | Redis-based distributed caching with TTL configuration |
| `observability-starter` | `acme.obs.*` | Micrometer common tags, URI cardinality guard (prevents /users/{id} explosion), trace-id response header |

## Quick Start

### Prerequisites

- **Java 21** (Temurin recommended)
- **Maven 3.9+**
- **Docker** and **Docker Compose**

### 1. Start Infrastructure

```bash
cd infra/docker
docker compose up -d
```

This starts PostgreSQL, Kafka (KRaft), Redis, Kafka UI, Jaeger, Prometheus, and Grafana.

| Service | URL |
|---------|-----|
| Kafka UI | http://localhost:8082 |
| Jaeger UI | http://localhost:16686 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |

### 2. Build All Modules

```bash
mvn clean install -DskipTests
```

### 3. Run Services

Start in order:

```bash
# 1. Config Server
cd infra/config-server && mvn spring-boot:run

# 2. Discovery Service
cd infra/discovery-service && mvn spring-boot:run

# 3. API Gateway
cd infra/api-gateway && mvn spring-boot:run

# 4. Example Service
cd services/example-service && mvn spring-boot:run
```

### 4. Test It

```bash
# Direct
curl http://localhost:8080/api/order

# Through gateway
curl http://localhost:8000/example/order
```

## Creating a New Service

1. **Copy** `services/example-service` as your template
2. **Update** `artifactId`, `name` in `pom.xml` and `spring.application.name`
3. **Add** the module to `services/pom.xml` `<modules>`
4. **Create** config at `infra/config-server/src/main/resources/configs/<your-service>/application.yml`
5. **Add** gateway route in `infra/api-gateway/src/main/resources/application.yml`
6. **Create** CI/CD workflows from existing templates under `.github/workflows/`
7. **Add** Docker + K8s manifests
8. **Write** tests extending `AbstractPostgresIntegrationTest` or `AbstractFullIntegrationTest`

### Service Package Structure

```
com.template.microservices.<service-name>/
├── api/controller/             REST controllers (thin, delegates to service)
├── application/service/        Business logic interfaces + impl/
├── domain/entity/              JPA entities, value objects
└── infrastructure/
    ├── configuration/          Spring config, topic definitions
    ├── messaging/
    │   ├── consumer/           Kafka consumers
    │   └── processor/          Outbox event producers
    └── repository/             Spring Data JPA repositories
```

## Key Patterns

### Transactional Outbox + Inbox

Never publish directly to Kafka in a business transaction. Instead:

```
Business TX: save entity + save to outbox table (same TX)
     ↓
OutboxScheduler polls outbox → EventPublisher sends to Kafka
     ↓
Consumer writes to inbox table (UUID dedup) → processes event
```

### Saga Orchestration

Define multi-step distributed transactions with automatic compensation:

```java
SagaDefinition<CreateOrderSagaContext> definition = SagaDefinition
    .<CreateOrderSagaContext>builder("create-order")
    .step("reserve-stock", reserveStockStep)
    .step("charge-payment", chargePaymentStep)
    .step("confirm-order", confirmOrderStep)
    .build();

// Each step commits independently — crash-recoverable
UUID sagaId = sagaOrchestrator.start(definition, context);
```

If step 3 fails → steps 2 and 1 are compensated in reverse order automatically.

### Two-Layer JWT Security

```
Client → API Gateway (validates JWT, extracts X-User-Id/X-User-Email headers)
              ↓
         Service (security-starter trusts gateway headers OR re-validates JWT)
```

### Error Handling Pipeline

```
Domain error → BusinessException(ErrorCode) → GlobalExceptionHandler → ApiResponse / ProblemDetail
```

## Infrastructure

### Docker Compose Profiles

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Development (all infra + services) |
| `docker-compose.test.yml` | CI/CD testing |
| `docker-compose.prod.yml` | Production-like setup |

### Kubernetes

Manifests in `infra/k8s/` include:
- Deployments with resource limits, health probes, and HPA
- JWT secret management via `jwt-secret.yaml`
- Services for all components

### CI/CD

Each service has independent GitHub Actions pipelines:
- `<service>-ci.yml` — Build, test, coverage check on push/PR
- `<service>-cd.yml` — Docker build, push to GHCR, deploy

## Observability

| Signal | Tool | Access |
|--------|------|--------|
| Metrics | Prometheus + Grafana | `:9090` / `:3000` |
| Traces | Jaeger (OTLP) | `:16686` |
| Logs | Structured (`@Slf4j`) | stdout / ELK |

All services expose `/actuator/prometheus` for scraping. The `observability-starter` adds common tags (`application`, `environment`) and prevents metric cardinality explosion from path variables.

## Agentic Development

This project is **co-developed with AI agents** using GitHub Copilot's agentic capabilities:

### Custom Skills (`.github/skills/`)

8 domain-specific skills that teach Copilot the project's patterns:

| Skill | Purpose |
|-------|---------|
| `api-gateway-patterns` | Gateway routes, rate limiting, reactive filters |
| `error-handling` | ErrorCode → BusinessException → GlobalExceptionHandler pipeline |
| `kafka-event-flow` | EventPublisher, EventWrapper, topic naming, DLT config |
| `outbox-inbox-pattern` | Transactional outbox/inbox lifecycle |
| `security-jwt-flow` | Two-layer JWT architecture |
| `service-scaffolding` | New service creation checklist |
| `starter-authoring` | `@AutoConfiguration` conventions |
| `testing-patterns` | Testcontainers, `@WebMvcTest`, assertion patterns |

### Custom Instructions (`.github/instructions/`)

Project-wide coding conventions enforced by Copilot:
- Constructor injection only, no `@Autowired`
- `ApiResponse<T>` wrapping for all endpoints
- Flyway for all schema changes
- `@Slf4j` only, never `System.out.println`
- Events implement `Event` interface, wrapped in `EventWrapper<T>`

### Agent Modes

Custom agent modes (X mode) for structured multi-phase execution:
**Plan → Implement → Review → Build → Report**

## Running Tests

```bash
# All tests
mvn clean test

# Single module
mvn clean test -pl starters/saga-starter

# With dependencies
mvn clean test -pl services/example-service -am
```

Integration tests use **Testcontainers** — Docker must be running. Extend:
- `AbstractPostgresIntegrationTest` — PostgreSQL only
- `AbstractKafkaIntegrationTest` — Kafka only
- `AbstractFullIntegrationTest` — PostgreSQL + Kafka

## Contributing

1. Follow the package structure and coding conventions
2. Every starter uses `@AutoConfiguration` + `@ConditionalOnMissingBean`
3. Properties go under `acme.*` namespace
4. Dependency flow: `commons → starters → services` (one direction)
5. Starters must not depend on other starters (only commons)

## License

This project is available as a **public template**. Use it to bootstrap your own microservice architectures.
