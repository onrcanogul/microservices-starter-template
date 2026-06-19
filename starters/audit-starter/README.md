# audit-starter

Hibernate Envers entity audit trail with MDC-sourced revision metadata. Namespace `acme.audit.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `AuditAutoConfiguration` | on `AuditReader` classpath, after `HibernateJpaAutoConfiguration`; `@EntityScan` on starter package |
| `HibernatePropertiesCustomizer` (`enversHibernatePropertiesCustomizer`) | maps props to Envers settings |
| `AuditQueryService` | wraps `AuditReader`: `getHistory`, `getAtRevision`, `getAtPointInTime`, revision lists |
| `AuditRevision<T>` | record `(entity, CustomRevisionEntity, RevisionType)` |
| `CustomRevisionEntity` | `@RevisionEntity` table `revinfo`; cols `rev`/`revtstmp`/`user_id`/`user_email`/`correlation_id` |
| `CustomRevisionListener` | fills user/email/correlation from MDC |
| `AuditProperties` | bound config |

## Config (`acme.audit.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | activate auditing |
| `store-data-at-delete` | `true` | Envers `store_data_at_delete` |
| `modified-flags` | `false` | Envers `global_with_modified_flag` |
| `audit-table-suffix` | `_aud` | Envers `audit_table_suffix` |
| `revision-table-name` | `revinfo` | Envers `revision_table_name` |

MDC keys (`userId`/`userEmail`/`correlationId`) come from logging-starter's `MdcFilter`; null if absent. Flyway tables must match configured names.

## Depends on
`common-core`

## See
`docs/patterns/audit-trail.md`
