# CLAUDE.md

This file is the **map** of the repository for AI agents (and humans). It does not restate the
details — it points to the one place each topic is documented. When a rule or a module changes,
update the linked document, not this file.

> The primary user is learning while building. Always explain **why** a decision is made, not just
> what. When you make a non-trivial change, add or update a short markdown doc under `docs/`
> describing the reasoning, the trade-offs, and the alternatives you rejected.

## What this is

An enterprise-grade microservices starter template (Java 21, Spring Boot 3.5, Spring Cloud 2025)
meant to be reused as a foundation and to scale from 100 to 1,000,000+ users. Shared infrastructure
ships as Spring Boot auto-configuration **starters** under the `acme.*` property namespace; business
code lives in **services**; cross-cutting platform pieces live in **infra**.

## Golden rules

These are the hard invariants. They are enforced by ArchUnit fitness functions in
[`architecture-tests/`](architecture-tests/README.md) and explained in full in
[`docs/constraints/layering.md`](docs/constraints/layering.md):

- Dependencies flow one way: **`commons` ← `starters` ← `services` / `infra`**.
- Starters are two-tiered: **foundation** (`persistence`, `kafka`) and **feature** (everything else).
  A feature starter may depend on `commons` + foundation starters, never on another feature starter.
  No dependency cycles.
- Run the checks: `mvn -B test -pl architecture-tests -am`.

Coding conventions (constructor injection only — no `@Autowired`; `ApiResponse<T>` for all endpoints;
Flyway for all schema changes; `@Slf4j`, never `System.out`; events implement `Event`, wrapped in
`EventWrapper<T>`) are the single source in [`docs/conventions.md`](docs/conventions.md). Only the
dependency-flow rule is enforced by a fitness function so far — the rest is a tracked follow-up.

## Module map

Each module documents itself in its own README. Start there before changing a module.

**commons** — shared contracts, no business logic. See [`commons/README.md`](commons/README.md).
- [`common-core`](commons/common-core/README.md) · [`common-messaging`](commons/common-messaging/README.md) · [`common-web`](commons/common-web/README.md) · [`common-test`](commons/common-test/README.md)
- Note: commons modules carry no Spring auto-configuration, **except** `common-web`, which
  deliberately ships `CommonWebAutoConfiguration` to wire the `GlobalExceptionHandler`. This is the
  one documented exception.

**starters** — Spring Boot auto-configuration, `acme.<ns>.*` namespaces, every bean
`@ConditionalOnMissingBean`. Authoring conventions: skill `starter-authoring`.
- Foundation: [`persistence-starter`](starters/persistence-starter/README.md) · [`kafka-starter`](starters/kafka-starter/README.md)
- Feature: [`outbox`](starters/outbox-starter/README.md) · [`inbox`](starters/inbox-starter/README.md) · [`saga`](starters/saga-starter/README.md) · [`security`](starters/security-starter/README.md) · [`resilience`](starters/resilience-starter/README.md) · [`cache`](starters/cache-starter/README.md) · [`idempotency`](starters/idempotency-starter/README.md) · [`logging`](starters/logging-starter/README.md) · [`scheduler-lock`](starters/scheduler-lock-starter/README.md) · [`audit`](starters/audit-starter/README.md) · [`webclient`](starters/webclient-starter/README.md) · [`observability`](starters/observability-starter/README.md)

**services** — business microservices.
- [`example-service`](services/example-service/README.md) — reference implementation wiring every pattern.
- [`inventory-service`](services/inventory-service/README.md) — choreographed stock-reservation saga with example-service (cross-service, event-driven).

**infra** — platform services.
- [`api-gateway`](infra/api-gateway/README.md) (:8000) · [`discovery-service`](infra/discovery-service/README.md) (:8761) · [`config-server`](infra/config-server/README.md) (:8888)

**architecture-tests** — [the fitness functions](architecture-tests/README.md) that enforce the golden rules.

## Knowledge base (`docs/`)

The cross-cutting, learning-oriented documentation lives under [`docs/`](docs/README.md), organised
by topic:

- [`docs/architecture/overview.md`](docs/architecture/overview.md) — runtime topology, request path, event path (outbox→inbox), dependency direction.
- [`docs/concepts/distributed-systems.md`](docs/concepts/distributed-systems.md) — delivery/consistency, CAP/PACELC, coordination, observability mapped to where each appears here.
- [`docs/constraints/`](docs/constraints/) — the enforced architectural invariants ([`layering.md`](docs/constraints/layering.md)).
- [`docs/patterns/`](docs/patterns/) — design-decision deep-dives: [`saga`](docs/patterns/saga.md) · [`scheduler-lock`](docs/patterns/scheduler-lock.md) · [`audit-trail`](docs/patterns/audit-trail.md) · [`choreographed-stock-reservation`](docs/patterns/choreographed-stock-reservation.md) · [`event-versioning`](docs/patterns/event-versioning.md) · [`structured-logging`](docs/patterns/structured-logging.md) · [`outbox-inbox`](docs/patterns/outbox-inbox.md) · [`jwt-two-layer`](docs/patterns/jwt-two-layer.md) · [`error-handling`](docs/patterns/error-handling.md).
- [`docs/operations/running-locally.md`](docs/operations/running-locally.md) — prerequisites, infra, startup order, smoke test, tests, troubleshooting.
- [`docs/conventions.md`](docs/conventions.md) — coding conventions (the single source; see golden rules above).
- [`docs/glossary.md`](docs/glossary.md) — definitions of the core concepts (Event, EventWrapper, ApiResponse, outbox/inbox, saga, upcaster, foundation vs feature starter).

Module-specific "what it does" documentation stays co-located as each module's `README.md` (see the
module map above). The reusable, task-oriented knowledge for *doing* work lives as skills under
[`.claude/skills/`](.claude/skills/) (Claude Code discovers these automatically; `.github/skills/`
holds thin pointers so Copilot stays in sync): `service-scaffolding`, `starter-authoring`,
`outbox-inbox-pattern`, `kafka-event-flow`, `security-jwt-flow`, `api-gateway-patterns`,
`error-handling`, `testing-patterns`, `architecture-fitness`, `docs-authoring`. Read the relevant
skill before doing that kind of work.

## Build & test

```bash
mvn clean install -DskipTests          # build everything
mvn -B test -pl architecture-tests -am  # run architecture fitness functions
mvn clean test -pl starters/saga-starter            # one module
mvn clean test -pl services/example-service -am     # a service + its deps
```

Integration tests use Testcontainers, so Docker must be running. Local infra:
`cd infra/docker && docker compose up -d`.

## Creating a new service

Follow the `service-scaffolding` skill. In short: copy `services/example-service`, update
`artifactId` / `spring.application.name`, register the module in `services/pom.xml`, add config under
`infra/config-server/src/main/resources/configs/<service>/`, add a gateway route, add CI/CD
workflows and Docker/K8s manifests, and write tests extending the `Abstract*IntegrationTest` bases.
