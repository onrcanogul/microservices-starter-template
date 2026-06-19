# example-service

Reference microservice wiring every pattern/starter around a small `Order` domain. Base package `com.template.microservices.example`.

## Patterns demonstrated
| Pattern | Where |
|---------|-------|
| API envelope | `ApiResponse` in `api/controller/OrderController` |
| idempotency | `@Idempotent` on `POST /api/order`, `POST /api/order/saga` |
| method security | `@PreAuthorize` on `OrderController` |
| saga orchestration | `infrastructure/configuration/SagaConfig` → `ReserveStockStep` → `ChargePaymentStep` → `ConfirmOrderStep`; context `application/service/saga/CreateOrderSagaContext` |
| compensation | `compensate(...)` per step; `@SagaRollback` on `consumer/PaymentFailedConsumer` |
| outbox | `messaging/processor/OrderCreatedProducer` → `OutboxService` |
| inbox | `consumer/OrderConsumer`, `PaymentFailedConsumer` → `InboxService`; drained by `application/service/inbox/ExampleInboxProcessor` |
| event versioning | `messaging/OrderCreatedEvent` `@EventVersion(2)`; `messaging/upcaster/OrderCreatedV1ToV2Upcaster` (v1→v2 adds `customerEmail`) |
| Kafka topics + DLT | `infrastructure/configuration/TopicConfig` (`orders.created` + `.DLT`) |
| audit + soft delete | `domain/entity/Order` `@Audited` + `ISoftDelete`; `OrderServiceImpl.delete` |
| stock-reservation saga (choreography) | `messaging/processor/{StockReservationRequested,StockReleaseRequested}Producer`; `consumer/Stock{Reserved,ReservationFailed,Released}Consumer`; `ExampleInboxProcessor` branches; `Order.status` PENDING→CONFIRMED/REJECTED/CANCELLED — see [docs/patterns/choreographed-stock-reservation.md](../../docs/patterns/choreographed-stock-reservation.md) |

## Package structure
```
com.template.microservices.example
├── api/controller            # OrderController: GET/POST /api/order, POST /api/order/saga
├── application/service       # order/, inbox/, saga/
├── domain/entity             # Order (@Audited, soft-deletable)
└── infrastructure            # configuration/, messaging/{consumer,processor,upcaster}, repository/
```

## Run
Needs Postgres, Kafka, Redis + infra (`infra/docker/docker-compose.yml`). `JWT_SECRET` required (secured endpoints).
```bash
docker compose -f infra/docker/docker-compose.yml up -d
./mvnw -pl services/example-service -am spring-boot:run
```
Direct: `:8080` (`/api/order`). Via gateway: `:8000` (`/example/**` → `/api/...`).

## Migrations
Flyway `db/migration`, `ddl-auto: none`.
- V1 `create_orders_table` — `orders` (sku, amount, audit/soft-delete)
- V2 `create_outbox_table` — `outbox` + unpublished partial index
- V3 `create_inbox_table` — `inbox` keyed by idempotent_token
- V4 `add_outbox_correlation_id` — `correlation_id` on outbox
- V5 `create_saga_tables` — `saga_instance`, `saga_step_execution`
- V6 `add_event_version_columns` — `version` on outbox/inbox
- V7 `create_audit_tables` — Envers `revinfo`, `orders_aud`
- V8 `add_order_status` — `status` on `orders` (saga state)
