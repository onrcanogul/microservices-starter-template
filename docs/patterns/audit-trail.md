# Entity Audit Trail

**Decision:** Use Hibernate Envers. `@Audited` entities get shadow `_AUD` tables; every TX writes a revision to `revinfo`. A custom `RevisionListener` captures user context from MDC; `AuditQueryService` wraps `AuditReader`.

**Why:**
- `IInsertAuditing`/`IUpdateAuditing` only store created/updated timestamps — no previous values, no queryable history
- Compliance/debugging need "who changed what, when, prior value"
- Envers is battle-tested (Hibernate core, 10+ years), zero-code per entity
- Transactional consistency: audit rows commit in the same TX as business data
- MDC integration records real userId/email/correlationId (also fixed `persistence-starter` `AuditorAware` to read userId from MDC instead of hardcoded `"SYSTEM"`)

**Alternatives rejected:**
- Custom JPA event listeners (`@PostPersist`/etc.) — reinvents table mgmt, serialization, query API per entity; worse queries
- DB triggers — run outside app context, no user identity/MDC, not portable, hard to test/version
- Event sourcing — fundamental architecture choice, not a bolt-on; outbox/inbox already give reliable events
- Spring Data Envers — forces `RevisionRepository` inheritance; raw `AuditReader` is more flexible and type-safe (can add later)

**Trade-offs:**
- Doubles storage for audited entities
- Coupled to Hibernate (not JPA-portable)
- Complex queries slow without proper indexing
- Cannot audit non-entity changes (e.g. external API calls); user context null without logging-starter
- Behavioral change: pre-update rows keep `"SYSTEM"`; new ops record real user (falls back to `"SYSTEM"` when MDC empty, e.g. scheduled tasks)

**Implementation:** `audit-starter`: `CustomRevisionEntity` (userId/email/correlationId), `CustomRevisionListener` (from MDC), `AuditQueryService`, `AuditAutoConfiguration`, `AuditProperties` (`acme.audit.*`). Scale: monitor/partition `_AUD` by timestamp, index `revinfo.revtstmp`, archive to read replicas / cold storage at high volume.
