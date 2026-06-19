# Saga

**Decision:** Lightweight embedded saga support: orchestration (central engine driving sequential steps), **async orchestration** (a step may publish a request, suspend, and resume on the reply), and choreography (event-driven via outbox/inbox + `@SagaRollback`). State persisted in PostgreSQL.

**Why:**
- Distributed transactions (order = inventory + payment + shipping) need partial-failure handling
- Automatic compensation so developers don't manually track undo steps
- Crash recovery: durably persisted state lets a stuck saga resume/compensate on restart
- Operational visibility: saga state queryable in DB for monitoring/debugging
- **Async cross-service steps:** the orchestrator was synchronous/in-process — a step could not await a Kafka reply from another service. Async orchestration adds suspend/resume so a step can request → park (release the thread) → resume on reply, without blocking the caller and without the engine knowing about Kafka.
- Must start with plain `spring-boot:run` — no external server

**Choreography vs orchestrated-async (when to pick which):**
- **Choreography** — each service reacts to events and emits the next; no central record. Best for simple, linear, loosely-coupled flows (see [`choreographed-stock-reservation.md`](choreographed-stock-reservation.md)). Hard to get a single-pane saga view; compensation is itself choreographed.
- **Orchestrated-async** — one owner drives the flow and persists a single `saga_instance` (queryable status, deadlines, retries, central compensation), while still talking to other services asynchronously. Prefer it for multi-step flows that need a central view, deadlines/timeout-driven compensation, or branching beyond a couple of hops.

**Alternatives rejected:**
- Temporal/Camunda — heavy ops dependency, separate server, vendor lock-in, overkill for a starter
- Axon Framework — framework-level coupling, forces Axon's worldview on every service
- Choreography-only — complex flows become spaghetti, no central saga view (so we support BOTH, now including async orchestration)
- Synchronous blocking on the reply (hold the thread/request) — wastes threads, breaks on restart, couples availability; async suspend/resume avoids it
- Multi-level await / nested suspends — kept to **single-level await** (`onReply` returns Success/Failure, never Suspended) to keep state machine and recovery tractable

**Trade-offs:**
- Sequential steps only (v1) — parallel adds partial-compensation complexity
- Single-instance scheduler (no distributed lock yet); ThreadLocal context doesn't cross `@Async`/reactive
- Context serialized as JSON TEXT (any POJO, no schema change) — not strongly typed at rest
- **Async:** the engine stays Kafka-unaware on purpose — the step body publishes the request and the service/inbox layer maps reply→`resumeWithReply`. The reply→resume wiring is the integrator's responsibility (Stage 2). Single-level await only.
- For >10-step branching workflows, migrate to Temporal — this covers ~80% of cases

**Implementation:** `saga-starter`: `SagaOrchestrator` (engine: `start`/`resume`/`resumeById`/**`resumeWithReply`**), `SagaDefinition`/`SagaDefinitionBuilder` (DSL), `SagaInstance`/`SagaStepExecution` (entities), `SagaScheduler` (stuck/cleanup — RUNNING **and `WAITING_FOR_REPLY`** past deadline → compensate; a parked async step is never re-`execute()`d, so the request is not re-published), `SagaRollbackRegistry`, `SagaContextHolder`, `SagaProperties`. `common-messaging`: `SagaStepHandler<C>`, **`AsyncSagaStepHandler<C,R>`** (`onReply`, `replyType`), `StepOutcome<C>` (`+suspend(correlationKey, ctx)`), `StepResult` (`Success`/`Failure`/**`Suspended(correlationKey)`**), `SagaStatus` (`+WAITING_FOR_REPLY`), `StepStatus` (`+AWAITING`). A suspended saga persists `await_correlation_key` (indexed) + `await_step`; the reply locates it via `findByStatusAndAwaitCorrelationKey`. Per-step `TransactionTemplate` boundaries; definition registry via `SmartInitializingSingleton`; proxy-safe via `AopUtils`. Backward compatible: a definition with no async step behaves exactly as before.
