# saga-starter

Auto-configuration starter providing **orchestration-based** and **choreography-based** saga patterns for distributed transactions across microservices.

## Features

### Orchestration (SagaOrchestrator)
- **SagaDefinition DSL** — fluent builder to compose multi-step sagas
- **Persistent state** — saga instance + step execution tracked in PostgreSQL
- **Automatic compensation** — on step failure, preceding steps are compensated in reverse order
- **Stuck saga recovery** — `SagaScheduler` detects timed-out sagas and triggers compensation
- **Cleanup** — configurable cron job removes old terminal sagas

### Choreography (SagaRollbackRegistry)
- **Runtime `@SagaRollback` processing** — scans annotated beans at startup, builds compensation map
- **SagaContextHolder** — ThreadLocal-based correlation context for event handlers
- **Correlation ID propagation** — outbox events carry correlation ID through Kafka headers

## Configuration

```yaml
acme:
  saga:
    enabled: true                    # enable/disable saga engine (default: true)
    timeout: 30m                     # saga deadline (default: 30 minutes)
    max-retries: 3                   # max recovery attempts for stuck sagas
    scheduler-rate: 30s              # polling interval for stuck saga detection
    cleanup:
      cron: "0 0 4 * * *"          # cleanup job schedule (default: 4 AM daily)
      retention: 30d                 # keep terminal sagas for 30 days
```

## Usage — Orchestration

### 1. Define a saga context

```java
public record CreateOrderSagaContext(
    Long orderId, String sku, Integer amount,
    boolean stockReserved, boolean paymentCharged
) {}
```

### 2. Implement step handlers

```java
@Component
public class ReserveStockStep implements SagaStepHandler<CreateOrderSagaContext> {
    @Override
    public StepResult execute(CreateOrderSagaContext context) {
        // call inventory service
        return StepResult.success("Stock reserved");
    }

    @Override
    public StepResult compensate(CreateOrderSagaContext context) {
        // release reservation
        return StepResult.success();
    }
}
```

### 3. Build the saga definition

```java
@Bean
public SagaDefinition<CreateOrderSagaContext> createOrderSaga(
        ReserveStockStep reserveStock,
        ChargePaymentStep chargePayment,
        ConfirmOrderStep confirmOrder) {
    return SagaDefinition
        .builder("CreateOrderSaga", CreateOrderSagaContext.class)
        .step("reserve-stock", reserveStock)
        .step("charge-payment", chargePayment)
        .step("confirm-order", confirmOrder)
        .build();
}
```

### 4. Start the saga

```java
@PostMapping("/api/order/saga")
public ResponseEntity<ApiResponse<UUID>> createOrder(@RequestBody CreateOrderRequest request) {
    var context = new CreateOrderSagaContext(request.orderId(), request.sku(), request.amount(), false, false);
    UUID sagaId = sagaOrchestrator.start(createOrderSagaDefinition, context);
    return ResponseEntity.ok(ApiResponse.ok(sagaId));
}
```

## Usage — Choreography

### 1. Annotate rollback consumers

```java
@Service
@SagaRollback(source = OrderCreatedEvent.class, sourcesProcessor = OrderCreatedProducer.class)
public class PaymentFailedConsumer implements Consumer<PaymentFailedEvent> {
    // ...
}
```

### 2. Query the registry

```java
@Autowired SagaRollbackRegistry registry;

if (registry.hasRollbackFor(OrderCreatedEvent.class)) {
    var handlers = registry.getRollbackHandlers(OrderCreatedEvent.class);
    // handlers contains PaymentFailedConsumer
}
```

### 3. Use correlation context

```java
SagaContextHolder.set(sagaId, correlationId);
try {
    outboxService.save("order.created", event, Order.class, orderId, correlationId.toString());
} finally {
    SagaContextHolder.clear();
}
```

## Flyway Migrations

Add this migration to your service:

```sql
-- V*__create_saga_tables.sql
CREATE TABLE saga_instance (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type       VARCHAR(255)    NOT NULL,
    correlation_id  UUID            NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    payload         TEXT,
    current_step    INT             NOT NULL DEFAULT 0,
    retry_count     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deadline_at     TIMESTAMPTZ
);

CREATE INDEX ix_saga_status ON saga_instance (status);
CREATE INDEX ix_saga_correlation ON saga_instance (correlation_id);

CREATE TABLE saga_step_execution (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_instance_id    UUID            NOT NULL REFERENCES saga_instance(id) ON DELETE CASCADE,
    step_name           VARCHAR(255)    NOT NULL,
    step_order          INT             NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    output              TEXT,
    failure_reason      VARCHAR(1024),
    executed_at         TIMESTAMPTZ,
    compensated_at      TIMESTAMPTZ
);

CREATE INDEX ix_step_saga_id ON saga_step_execution (saga_instance_id);
```

## Dependencies

- `common-messaging` — saga contracts (SagaStepHandler, SagaStatus, StepResult, @SagaRollback)
- `common-core` — shared utilities
- `persistence-starter` — JPA infrastructure (entities, repositories)

## Overriding Defaults

All beans use `@ConditionalOnMissingBean`. To override:

```java
@Bean
public SagaOrchestrator customOrchestrator(...) {
    // your custom implementation
}
```
