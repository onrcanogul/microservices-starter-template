# inventory-service

Owns stock and reservations. Plays the **inventory side of the choreographed stock-reservation saga**
with `example-service` (the order side). Base package `com.template.microservices.inventory`. Port `8081`,
own database `inventorydb` (DB-per-service).

## Role in the saga (choreography, not orchestration)
This flow is event-driven choreography over the transactional outbox/idempotent inbox — **not** the
in-process saga engine (which is synchronous and cannot await a Kafka reply). See the decision record:
[`docs/patterns/choreographed-stock-reservation.md`](../../docs/patterns/choreographed-stock-reservation.md).

```
example: createOrder (PENDING) ──stock.reservation.requested──▶ inventory: reserve()
                                                                   │ enough → stock.reserved ──▶ example: order CONFIRMED
                                                                   └ not    → stock.reservation.failed ──▶ example: order REJECTED
example: cancel (CANCELLED) ─────stock.release.requested────────▶ inventory: release() ── stock.released ──▶ example: log
```

## Canonical contract (topic == type string, dotted)
| Topic | Event (`common-messaging` `event/stock/`) | Direction |
|-------|--------------------------------------------|-----------|
| `stock.reservation.requested` | `StockReservationRequestedEvent` | example → inventory |
| `stock.reserved`              | `StockReservedEvent`             | inventory → example |
| `stock.reservation.failed`    | `StockReservationFailedEvent`    | inventory → example |
| `stock.release.requested`     | `StockReleaseRequestedEvent`     | example → inventory |
| `stock.released`              | `StockReleasedEvent`             | inventory → example |
Each topic also has a `.DLT` (declared in `infrastructure/configuration/TopicConfig`).

## Patterns demonstrated
| Pattern | Where |
|---------|-------|
| consume → inbox | `messaging/consumer/{StockReservationRequested,StockReleaseRequested}Consumer` → `InboxService` |
| inbox processor | `application/service/inbox/InventoryInboxProcessor` (branches on event FQCN) |
| business logic (idempotent) | `application/service/inventory/InventoryServiceImpl.reserve/release` — keyed by `StockReservation.orderId` |
| emit via outbox | `messaging/processor/{StockReserved,StockReservationFailed,StockReleased}Producer` → `OutboxService` |
| API envelope | `ApiResponse` in `api/controller/StockController` (`GET /api/stock/{sku}`, public) |
| audit + soft delete | `domain/entity/{Stock,StockReservation}` `@Audited` + `ISoftDelete`; audit columns auto-populated via Spring Data JPA auditing |

## Package structure
```
com.template.microservices.inventory
├── api/controller            # StockController: GET /api/stock/{sku}
├── application/service       # inventory/ (reserve, release, getBySku), inbox/ (InventoryInboxProcessor)
├── domain/entity             # Stock, StockReservation (@Audited, soft-deletable)
└── infrastructure            # configuration/TopicConfig, messaging/{consumer,processor}, repository/
```

## Design notes
- **Idempotency:** one `StockReservation` per `orderId` (UNIQUE). A replayed `reservation.requested` does
  not decrement stock again; `release` only acts on a `RESERVED` row, so a replayed `release.requested` is a no-op.
- **Audit auto-population:** `Stock`/`StockReservation` are persisted at runtime, so they wire Spring Data
  JPA auditing (`@CreatedBy`/`@CreatedDate`, filled by the persistence-starter's `AuditorAware` → MDC `userId`
  or `SYSTEM`). The `Order` entity in example-service is not auto-populated by the template today (it was
  never persisted in the original flow) — both order and inventory entities now wire it.
- **Dependencies:** mirrors example-service's starter set (outbox/inbox/kafka/security/audit/saga/…). Inventory
  takes part by choreography, so the saga engine is unused here; saga + audit tables are still migrated so the
  copied starters boot cleanly.

## Run
Needs Postgres (`inventorydb`), Kafka, Redis + infra (`infra/docker/docker-compose.yml`). `JWT_SECRET` required.
```bash
docker compose -f infra/docker/docker-compose.yml up -d
./mvnw -pl services/inventory-service -am spring-boot:run
```
Observe: `curl :8081/api/stock/SKU-001` (public). Via gateway: `:8000/inventory/stock/SKU-001` → `/api/stock/...`.

## Migrations
Flyway `db/migration`, `ddl-auto: none`.
- V1 `create_stock_tables` — `stock`, `stock_reservation` (+ seed rows: `SKU-001`, `SKU-002`, `SKU-OUT-OF-STOCK`)
- V2 `create_outbox_table` · V3 `create_inbox_table` · V4 `add_outbox_correlation_id` (copied from example)
- V5 `create_saga_tables` — present so saga-starter boots; orchestration unused in this service
- V6 `add_event_version_columns` — `version` on outbox/inbox
- V7 `create_audit_tables` — Envers `revinfo`, `stock_aud`, `stock_reservation_aud`
