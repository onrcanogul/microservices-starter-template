# Saga Starter — Design Decisions

**Date**: 2026-04-08  
**Author**: Template Team  
**Status**: Implemented

## What Was Done

Implemented a full `saga-starter` module providing both **orchestration-based** and **choreography-based** saga patterns for managing distributed transactions across microservices.

### Orchestration Components
- `SagaStepHandler<C>` interface — execute + compensate contract (in common-messaging)
- `SagaDefinition<C>` + `SagaDefinitionBuilder<C>` — fluent DSL for composing multi-step sagas
- `SagaOrchestrator` — execution engine: drives steps forward, persists state, triggers compensation on failure
- `SagaInstance` + `SagaStepExecution` entities — persistent saga tracking in PostgreSQL
- `SagaScheduler` — detects stuck/timed-out sagas, cleanup job for terminal sagas
- `SagaProperties` — configurable timeout, retry, scheduler rate, cleanup retention

### Choreography Components
- `SagaRollbackRegistry` — scans `@SagaRollback` annotated beans at startup, builds runtime compensation map
- `SagaContextHolder` — ThreadLocal-based correlation context for event handlers
- Enhanced `OutboxService` with optional `correlationId` parameter
- Enhanced `OutboxProcessor` to propagate `CORRELATION_ID` header to Kafka

### Supporting Changes
- Added `SagaStatus`, `StepStatus`, `StepResult` (sealed interface) to common-messaging
- Added `correlationId` column to outbox entity and migration
- Flyway migrations for saga tables

## Why

The previous saga implementation was a skeleton: `SagaStep<P,R>` (unused interface) and `@SagaRollback` (annotation never processed at runtime). This left developers without tools to implement actual cross-service distributed transactions — one of the hardest problems in microservices.

A starter template claiming production-readiness must provide opinionated solutions for:
1. **Data consistency across services** — when an order creation involves inventory + payment + shipping, partial failure must be handled
2. **Automatic compensation** — developers shouldn't manually track which steps to undo
3. **Recovery from crashes** — if the orchestrator crashes mid-saga, the saga must resume or compensate on restart
4. **Operational visibility** — saga state persisted in DB enables monitoring and debugging

## Alternatives Considered

### 1. Temporal / Camunda (External Orchestrator)
- **Pro**: Battle-tested, rich workflow features, replay debugging
- **Con**: Heavy operational dependency (separate server), vendor lock-in, overkill for a starter template
- **Rejected**: We want a lightweight, embedded solution that starts with `spring-boot:run`

### 2. Axon Framework
- **Pro**: Mature CQRS/ES + Saga support, annotation-driven
- **Con**: Framework-level coupling (Axon-specific annotations everywhere), learning curve, heavyweight for a starter
- **Rejected**: Too opinionated for a template — forces Axon's worldview on all services

### 3. Choreography Only (Events + Outbox/Inbox)
- **Pro**: Fully decoupled, no single point of failure
- **Con**: Complex multi-step flows become spaghetti, hard to reason about compensation order, no central view of saga progress
- **Decision**: We support choreography AND orchestration. Simple flows use choreography (existing outbox/inbox). Complex multi-step flows use orchestration.

### 4. Saga State Machine (Spring State Machine)
- **Pro**: Formal state machine semantics
- **Con**: Spring State Machine is complex to configure, adds dependency, over-formalizes what is essentially a sequential pipeline
- **Rejected**: Our sequential step model is simpler and sufficient for most sagas

## How It Scales

### Small Scale (1-10 services)
- Orchestration runs in-process, same DB transaction as business logic
- SagaScheduler polls every 30s — negligible overhead
- Cleanup runs daily at 4 AM

### Medium Scale (10-50 services)
- Each service has its own saga tables — no cross-service DB dependency
- Correlation IDs enable tracing a saga across services via logs
- Stuck saga detection prevents resource leaks

### Large Scale (50+ services)
- If saga volume exceeds single-instance capacity, the `SagaScheduler` can be run on a single leader node using distributed locking (future enhancement)
- The saga tables can be partitioned by `saga_type` or `created_at`
- For very complex workflows (>10 steps, branching), consider migrating to Temporal — this starter covers 80% of use cases

## Key Design Decisions

1. **Per-step transactions** — Each step executes within its own `TransactionTemplate` boundary. Intermediate state is committed after each step, enabling crash recovery. The `SagaScheduler` can detect and resume stuck sagas because their state is durably persisted.
2. **Context passing via `StepOutcome<C>`** — `SagaStepHandler.execute()` returns `StepOutcome<C>(result, updatedContext)`, allowing immutable record contexts (e.g., `context.withStockReserved(true)`) to pass data between steps without mutable state.
3. **Definition registry for recovery** — `SagaOrchestrator` maintains a `ConcurrentHashMap<String, SagaDefinition<?>>` of registered definitions. `SagaScheduler` calls `resumeById()` to trigger compensation without needing the definition at scan time. Definitions are auto-registered via `SmartInitializingSingleton` at startup.
4. **Sequential steps only (v1)** — Parallel step execution adds compensation complexity (partial compensation). Sequential covers the majority of use cases.
5. **Context as JSON** — The saga context is serialized to TEXT column. This allows any record/POJO as context without schema changes.
6. **Backward-compatible** — Existing `SagaStep<P,R>` and `@SagaRollback` are untouched. New `SagaStepHandler<C>` is a separate interface.
7. **No Kafka dependency** — The saga-starter orchestrates local steps. For cross-service communication, use the existing outbox pattern from each step handler.
8. **Spring proxy-safe** — `SagaRollbackRegistry` uses `AopUtils.getTargetClass()` + `AnnotationUtils.findAnnotation()` to handle CGLIB proxies correctly.
9. **v1 limitations (documented)** — Single-instance scheduler (no distributed locking), ThreadLocal context doesn't propagate to `@Async`/reactive contexts. Addressed in v2 considerations.
