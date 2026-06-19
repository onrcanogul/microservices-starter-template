# Saga

**Decision:** Lightweight embedded saga support, both orchestration (central engine driving sequential steps) and choreography (event-driven via outbox/inbox + `@SagaRollback`). State persisted in PostgreSQL.

**Why:**
- Distributed transactions (order = inventory + payment + shipping) need partial-failure handling
- Automatic compensation so developers don't manually track undo steps
- Crash recovery: durably persisted state lets a stuck saga resume/compensate on restart
- Operational visibility: saga state queryable in DB for monitoring/debugging
- Must start with plain `spring-boot:run` — no external server

**Alternatives rejected:**
- Temporal/Camunda — heavy ops dependency, separate server, vendor lock-in, overkill for a starter
- Axon Framework — framework-level coupling, forces Axon's worldview on every service
- Choreography-only — complex flows become spaghetti, no central saga view (so we support BOTH)
- Spring State Machine — complex config, over-formalizes a sequential pipeline

**Trade-offs:**
- Sequential steps only (v1) — parallel adds partial-compensation complexity
- Single-instance scheduler (no distributed lock yet); ThreadLocal context doesn't cross `@Async`/reactive
- Context serialized as JSON TEXT (any POJO, no schema change) — not strongly typed at rest
- For >10-step branching workflows, migrate to Temporal — this covers ~80% of cases

**Implementation:** `saga-starter`: `SagaOrchestrator` (engine), `SagaDefinition`/`SagaDefinitionBuilder` (DSL), `SagaInstance`/`SagaStepExecution` (entities), `SagaScheduler` (stuck/cleanup), `SagaRollbackRegistry`, `SagaContextHolder`, `SagaProperties`. `common-messaging`: `SagaStepHandler<C>`, `StepOutcome<C>`, `SagaStatus`/`StepStatus`/`StepResult`. Per-step `TransactionTemplate` boundaries; definition registry via `SmartInitializingSingleton`; proxy-safe via `AopUtils`. Cross-service calls go through outbox per step.
