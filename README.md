# Microservices Starter Template

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-brightgreen?logo=spring)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Production-ready microservices foundation. Shared infrastructure ships as Spring Boot
auto-configuration **starters** (`acme.*`); business code lives in **services**; platform pieces in
**infra**. Proven patterns pre-wired: transactional outbox/inbox, saga, JWT security, resilience,
caching, observability, structured logging, audit trail, event versioning, and per-service CI/CD.

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language / framework | Java 21 · Spring Boot 3.5.5 · Spring Cloud 2025.0.0 |
| Messaging | Apache Kafka 3.7 (KRaft) |
| Storage | PostgreSQL 16 · Redis 7 |
| Discovery / gateway / config | Eureka · Spring Cloud Gateway (reactive) · Config Server (native) |
| Observability | Micrometer · Prometheus · Grafana · Jaeger (OTLP) |
| Security / resilience | JWT (HMAC) + Spring Security · Resilience4j 2.2.0 |
| Testing / build | JUnit 5 · Testcontainers 1.20.4 · Maven (multi-module) |
| Runtime / CI | Docker Compose · Kubernetes · GitHub Actions |

## Quick start

```bash
cd infra/docker && docker compose up -d      # Postgres, Kafka, Redis, Jaeger, Prometheus, Grafana
mvn clean install -DskipTests                 # build all modules
# run in order: config-server (:8888) -> discovery (:8761) -> api-gateway (:8000) -> example-service (:8080)
cd infra/config-server && mvn spring-boot:run
curl http://localhost:8080/api/order          # direct (works today)
# via gateway: /example/** rewrites to /api/example/** — currently mismatched with /api/order (see docs/architecture/overview.md)
```

Dashboards: Kafka UI `:8082` · Jaeger `:16686` · Prometheus `:9090` · Grafana `:3000` (admin/admin).

## Documentation

- **[`CLAUDE.md`](CLAUDE.md)** — the repository map: module layout, golden rules, build/test, knowledge-base index. Start here.
- **[`docs/`](docs/README.md)** — knowledge base: [constraints](docs/constraints/layering.md) · [patterns](docs/patterns/) · [glossary](docs/glossary.md) · [conventions](docs/conventions.md).
- Each module documents itself in its own `README.md`.

## New service

Copy `services/example-service`, follow the `service-scaffolding` skill, and see
[`CLAUDE.md`](CLAUDE.md#creating-a-new-service).
