# audit-starter

Entity change audit trail via Hibernate Envers with MDC-based revision metadata.

## Features

- **Automatic Entity Auditing** — Add `@Audited` to any JPA entity to track INSERT/UPDATE/DELETE history
- **Custom Revision Metadata** — Each revision captures `userId`, `userEmail`, `correlationId` from MDC
- **Audit Query Service** — Convenient API for querying entity history, point-in-time snapshots, and revision details
- **Configurable** — Table suffix, revision table name, store-data-at-delete, modified flags via `acme.audit.*`

## Quick Start

### 1. Add the dependencies

```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>audit-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-envers</artifactId>
</dependency>
```

> Both `spring-boot-starter-data-jpa` and `hibernate-envers` are optional in the starter, so consuming services must declare `hibernate-envers` explicitly.

### 2. Annotate entities with `@Audited`

```java
@Entity
@Audited
public class Order implements IInsertAuditing, IUpdateAuditing {
    // ...
}
```

### 3. Create Flyway migration for audit tables

```sql
-- Revision info table
CREATE TABLE revinfo (
    rev             BIGSERIAL       PRIMARY KEY,
    revtstmp        BIGINT          NOT NULL,
    user_id         VARCHAR(255),
    user_email      VARCHAR(255),
    correlation_id  VARCHAR(255)
);

-- Audit table for your entity (mirrors entity structure)
CREATE TABLE orders_aud (
    id          BIGINT          NOT NULL,
    rev         BIGINT          NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    -- ... all entity columns ...
    PRIMARY KEY (id, rev)
);
```

## Configuration

```yaml
acme:
  audit:
    enabled: true                    # Master switch (default: true)
    store-data-at-delete: true       # Capture entity state on DELETE (default: true)
    modified-flags: false            # Track per-property changes (default: false)
    audit-table-suffix: _aud         # Suffix for audit tables (default: _aud)
    revision-table-name: revinfo     # Revision info table name (default: revinfo)
```

## Revision Metadata

Each revision automatically captures from MDC (set by logging-starter's `MdcFilter`):

| Field | Source | Description |
|-------|--------|-------------|
| `userId` | `X-User-Id` header | Who made the change |
| `userEmail` | `X-User-Email` header | Email of who changed |
| `correlationId` | `X-Correlation-Id` header | Distributed tracing ID |
| `revtstmp` | System time | When the change happened |

## Querying Audit History

Inject `AuditQueryService` to query audit data:

```java
@RequiredArgsConstructor
@Service
public class OrderHistoryService {
    private final AuditQueryService auditQueryService;

    public List<AuditRevision<Order>> getOrderHistory(Long orderId) {
        return auditQueryService.getHistory(Order.class, orderId);
    }

    public Order getOrderAtRevision(Long orderId, long revision) {
        return auditQueryService.getAtRevision(Order.class, orderId, revision);
    }

    public Order getOrderAtPointInTime(Long orderId, Instant at) {
        return auditQueryService.getAtPointInTime(Order.class, orderId, at);
    }
}
```

### AuditRevision record

```java
record AuditRevision<T>(
    T entity,                      // Entity state at this revision
    CustomRevisionEntity revision, // Who, when, correlationId
    RevisionType type              // ADD, MOD, or DEL
)
```

## How It Works

1. When an `@Audited` entity is modified within a JPA transaction, Hibernate Envers automatically creates a revision record
2. `CustomRevisionListener` reads `userId`, `userEmail`, `correlationId` from SLF4J MDC and stores them in the `revinfo` table
3. The entity's state is copied to the `_aud` shadow table with the revision number and operation type
4. `AuditQueryService` wraps Envers' `AuditReader` for convenient querying

## Integration with logging-starter

The MDC keys (`userId`, `userEmail`, `correlationId`) used by the `CustomRevisionListener` are populated by logging-starter's `MdcFilter`. Without logging-starter, revision metadata fields will be null (the audit trail still records timestamps and entity data).

## AuditorAware Integration

The persistence-starter's `AuditorAware` has been updated to read `userId` from MDC. This means `@CreatedBy` and `@LastModifiedBy` fields on entities using Spring Data JPA auditing will also be populated with the actual user ID instead of `"SYSTEM"`.

**Note:** This is a behavioral change — existing entities audited before this update will have `"SYSTEM"` as their `createdBy`/`updatedBy`. New operations will use the real user ID from MDC (falls back to `"SYSTEM"` when MDC is empty).

## Important Notes

- **Flyway migrations must match configuration.** If you customize `acme.audit.revision-table-name` or `acme.audit.audit-table-suffix`, ensure your Flyway migration table names match the configured values. The defaults (`revinfo` and `_aud`) match the provided migration template.
