# common-messaging

Messaging contracts: `Event` base, `EventWrapper` envelope, producer/consumer interfaces, event versioning + upcasting, saga step abstractions. No Spring auto-config.

## Key types
| Type | Role |
|------|------|
| `Event` | Marker interface for all domain events |
| `EventWrapper<T>` | Record envelope: `id:UUID, type, source, time:Instant, event:T, headers:Map<String,String>` |
| `MessageHeaders` | Header constants: `x-trace-id`, `x-correlation-id`, `x-causation-id`, `x-key`, `x-event-version`, `x-user-id` |
| `Producer<E extends Event>` / `Consumer<R extends Event>` | Publish / handle contracts |
| `@EventVersion` | Type annotation tagging an event's schema version (default 1) |
| `EventVersionUtil` | `getVersion(eventClass)`; `DEFAULT_VERSION=1` |
| `EventUpcaster` | `eventType()`, `fromVersion()`, `toVersion()`, `upcast(jsonPayload)` — one version hop |
| `EventUpcastChain` | Composes upcasters; `upcast(type,from,to,payload)`, `hasUpcastersFor(type)`, `empty()` |
| `SagaStep<P,R>` / `SagaStepHandler<C>` | Saga step + handler; handler returns `StepOutcome<C>` (`success`/`failure`) |
| `StepResult` | Sealed: `Success(output)` \| `Failure(reason,cause)` |
| `StepStatus` | PENDING, SUCCEEDED, FAILED, COMPENSATED |
| `SagaStatus` | STARTED, RUNNING, COMPENSATING, COMPLETED, COMPENSATED |
| `@SagaRollback` | Marks a compensating consumer |

## Depends on
none (pure base)
