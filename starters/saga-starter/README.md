# saga-starter

Saga support: persistent-state orchestration, choreography rollback via `@SagaRollback`, and a recovery scheduler for stuck sagas. Config under `acme.saga.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `SagaAutoConfiguration` | Gated on `acme.saga.enabled` + `SagaStepHandler` on classpath; `@EnableScheduling`, JPA/entity scan |
| `SagaProperties` | `@ConfigurationProperties("acme.saga")` |
| `SagaOrchestrator` | Drives multi-step sagas; `register`, `start`, `resume`, `resumeById` |
| `SagaDefinition` / `SagaDefinitionBuilder<C>` | Declares ordered steps via `step(name, SagaStepHandler<C>)` |
| `SagaScheduler` | Polls for stuck sagas and triggers compensation/recovery |
| `SagaRollbackRegistry` | Scans `@SagaRollback` handlers; maps source-event class to rollback entries |
| `SagaContextHolder` | Thread-local `SagaContext(sagaId, correlationId)` for choreography |
| `SagaInstance` / `SagaStepExecution` | `@Entity` persistent saga + step state |
| `TransactionTemplate` (`sagaTransactionTemplate`) | Tx boundary for orchestrator state writes |

## Config (`acme.saga.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | Enables the saga engine |
| `timeout` | `30m` | Time before a saga is considered stuck |
| `max-retries` | `3` | Retries for a stuck saga before `FAILED` |
| `scheduler-rate` | `30s` | Recovery scheduler poll interval |
| `cleanup.cron` | `0 0 4 * * *` | Cleanup schedule |
| `cleanup.retention` | `30d` | Retention for completed/failed sagas |

## Depends on
`common-messaging` (`SagaStepHandler`), `persistence-starter` (foundation), `scheduler-lock-starter`.

## See
`docs/patterns/saga.md`
