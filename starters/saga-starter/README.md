# saga-starter

Saga support: persistent-state orchestration (sync **and async suspend/resume**), choreography rollback via `@SagaRollback`, and a recovery scheduler for stuck sagas. Config under `acme.saga.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `SagaAutoConfiguration` | Gated on `acme.saga.enabled` + `SagaStepHandler` on classpath; `@EnableScheduling`, JPA/entity scan |
| `SagaProperties` | `@ConfigurationProperties("acme.saga")` |
| `SagaOrchestrator` | Drives multi-step sagas; `register`, `start`, `resume`, `resumeById`, `resumeWithReply` |
| `SagaDefinition` / `SagaDefinitionBuilder<C>` | Declares ordered steps via `step(name, SagaStepHandler<C>)` (sync or async) |
| `SagaScheduler` | Polls for stuck sagas (RUNNING + `WAITING_FOR_REPLY` past deadline) and triggers compensation |
| `SagaRollbackRegistry` | Scans `@SagaRollback` handlers; maps source-event class to rollback entries |
| `SagaContextHolder` | Thread-local `SagaContext(sagaId, correlationId)` for choreography |
| `SagaInstance` / `SagaStepExecution` | `@Entity` persistent saga + step state (`await_correlation_key`, `await_step` for async) |
| `TransactionTemplate` (`sagaTransactionTemplate`) | Tx boundary for orchestrator state writes |

## Async steps (suspend / resume)
A step that must await a cross-service reply implements `AsyncSagaStepHandler<C, R>` (in `common-messaging`):

```java
class ReserveStockStep implements AsyncSagaStepHandler<OrderCtx, StockReservedReply> {
    public StepOutcome<OrderCtx> execute(OrderCtx ctx) {
        producer.requestReservation(ctx.orderId(), ctx.sku(), ctx.amount()); // publish request (outbox)
        return StepOutcome.suspend("order-" + ctx.orderId(), ctx);            // park, release the thread
    }
    public StepOutcome<OrderCtx> onReply(OrderCtx ctx, StockReservedReply reply) {
        return reply.reserved() ? StepOutcome.success(ctx) : StepOutcome.failure("no stock");
    }
    public Class<StockReservedReply> replyType() { return StockReservedReply.class; }
    public StepResult compensate(OrderCtx ctx) { /* release */ return StepResult.success(); }
}
```

Lifecycle: `execute()` returns `suspend(correlationKey, ctx)` → saga persists `WAITING_FOR_REPLY` + `awaitCorrelationKey` and the calling thread returns. When the reply arrives, the service/inbox layer calls `orchestrator.resumeWithReply(correlationKey, reply)`:
- found + `WAITING_FOR_REPLY` → `onReply(ctx, reply)`; **Success** advances the saga (next sync step runs, next async step suspends again, or the saga COMPLETEs), **Failure** compensates prior steps in reverse.
- not found / not waiting → **no-op** (idempotent under at-least-once delivery).

Rules: the engine stays Kafka-unaware (the step body publishes; you wire reply→`resumeWithReply`). **Single-level await** — `onReply` must return Success/Failure, never `suspend`. On timeout the scheduler compensates a `WAITING_FOR_REPLY` saga without re-running `execute()` (no duplicate request).

## Config (`acme.saga.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | Enables the saga engine |
| `timeout` | `30m` | Time before a saga (incl. one awaiting a reply) is considered stuck |
| `max-retries` | `3` | Retries for a stuck saga before `FAILED` |
| `scheduler-rate` | `30s` | Recovery scheduler poll interval |
| `cleanup.cron` | `0 0 4 * * *` | Cleanup schedule |
| `cleanup.retention` | `30d` | Retention for completed/failed sagas |

## Depends on
`common-messaging` (`SagaStepHandler`, `AsyncSagaStepHandler`), `persistence-starter` (foundation), `scheduler-lock-starter`.

## See
`docs/patterns/saga.md`
