# Entity Audit Trail via Hibernate Envers

**Date:** 2026-04-11  
**Module:** audit-starter (`starters/audit-starter/`)

## Problem

`IInsertAuditing`/`IUpdateAuditing` only track `createdAt`/`createdBy` and `updatedAt`/`updatedBy` — simple timestamps on the entity itself. There is no queryable audit history: when a field changes, the previous value is lost. At scale, compliance and debugging both require knowing **who changed what, when, and what the previous value was**.

## Approach: Hibernate Envers

We chose **Hibernate Envers** — Hibernate's built-in auditing module that has been part of the Hibernate ecosystem for 10+ years.

### How it works

1. Annotate an entity with `@Audited`
2. Envers automatically creates shadow `_AUD` tables that mirror the entity structure
3. Every INSERT/UPDATE/DELETE within a JPA transaction creates a **revision** in the `revinfo` table and copies the entity state to the `_AUD` table
4. A custom `RevisionListener` captures user context (userId, userEmail, correlationId) from SLF4J MDC
5. `AuditQueryService` wraps Envers' `AuditReader` for querying: history, point-in-time snapshots, revision details

### Components

| Component | Purpose |
|-----------|---------|
| `CustomRevisionEntity` | Extended REVINFO with userId, userEmail, correlationId |
| `CustomRevisionListener` | Populates revision metadata from MDC |
| `AuditQueryService` | Convenience wrapper for Envers AuditReader queries |
| `AuditAutoConfiguration` | Configures Envers properties + registers beans |
| `AuditProperties` | `acme.audit.*` configuration namespace |

### AuditorAware Fix

Updated `persistence-starter`'s `AuditorAware` to read `userId` from MDC instead of hardcoded `"SYSTEM"`. This ensures `@CreatedBy`/`@LastModifiedBy` fields use the actual user identity.

**Behavioral change:** Existing entities audited before this update will have `"SYSTEM"` as their `createdBy`/`updatedBy`. After this update, new operations will record the real user ID from MDC (falls back to `"SYSTEM"` when MDC is empty, e.g., scheduled tasks).

## Alternatives Considered

### 1. Custom JPA Event Listeners (`@PostPersist`, `@PostUpdate`, `@PreRemove`)

**Rejected.** Requires writing custom audit table management, entity-to-audit-record serialization, query API, and schema management for every audited entity. Significantly more code with worse query capabilities than Envers. Reinvents what Envers already does.

### 2. Database-level triggers (PostgreSQL triggers)

**Rejected.** Triggers run outside the application context — no access to user identity, correlation IDs, or MDC. Harder to test, version, and maintain. Not portable across databases.

### 3. Event Sourcing

**Rejected for this scope.** Event sourcing is an architectural pattern where the event log IS the source of truth. It's a fundamental design choice, not something you bolt onto existing CRUD entities. The outbox/inbox patterns already give us reliable event publishing; full event sourcing would require rethinking the entire persistence model.

### 4. Spring Data Envers (`spring-data-envers`)

**Considered.** Spring Data Envers adds `RevisionRepository` for Spring Data repositories. We chose to use the lower-level `AuditReader` API via our `AuditQueryService` instead because:
- More flexible query capabilities
- Doesn't force inheritance on repositories
- Our `AuditQueryService` provides a cleaner API with `CustomRevisionEntity` type safety

We can add `spring-data-envers` support later if repository-level querying is desired.

## Scalability

| Scale | Consideration |
|-------|--------------|
| **100 users** | Works out of the box. Audit tables grow slowly. |
| **10,000 users** | Monitor `_AUD` table sizes. Consider partitioning by revision timestamp. |
| **100,000+ users** | Archive old revisions. Add `revinfo.revtstmp` index for time-range queries. Consider read replicas for audit queries. |
| **1,000,000+ users** | Move audit query workload to dedicated read replicas. Partition `_AUD` tables by date. Consider archival to cold storage (S3/GCS) for compliance. |

## Configuration

```yaml
acme:
  audit:
    enabled: true
    store-data-at-delete: true
    modified-flags: false
    audit-table-suffix: _aud
    revision-table-name: revinfo
```

## Trade-offs

| Pro | Con |
|-----|-----|
| Zero-code auditing per entity (`@Audited`) | Doubles storage for audited entities |
| Battle-tested (Hibernate core team) | Coupled to Hibernate (not JPA-portable) |
| Built-in query API (`AuditReader`) | Complex queries can be slow without proper indexing |
| Transactional consistency (same TX as business data) | Cannot audit non-entity changes (e.g., external API calls) |
| MDC integration gives full user context | Requires logging-starter for user context; null without it |
