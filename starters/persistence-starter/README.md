# persistence-starter

JPA/Hibernate + HikariCP tuning, transaction template, and JPA auditing under `acme.persistence.*`. FOUNDATION tier.

## Beans / key types
| Type | Role |
|------|------|
| `PersistenceAutoConfiguration` | Wires Hibernate defaults, `TransactionTemplate`, JPA auditing |
| `PersistenceProperties` | `@ConfigurationProperties("acme.persistence")` |
| `HibernatePropertiesCustomizer` | Applies time-zone, batch, ordering, ddl-auto, show-sql to Hibernate |
| `TransactionTemplate` | Programmatic tx with default timeout (only when a `PlatformTransactionManager` bean exists) |
| `AuditorAware<String>` | Resolves auditor from MDC `userId`, falls back to `SYSTEM` |

## Config (`acme.persistence.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `open-in-view` | `false` | OSIV flag |
| `ddl-auto` | `none` | `hibernate.hbm2ddl.auto` |
| `time-zone` | `UTC` | `hibernate.jdbc.time_zone` |
| `jdbc-batch-size` | `50` | `hibernate.jdbc.batch_size` (applied if > 0) |
| `order-inserts` / `order-updates` | `true` / `true` | Hibernate insert/update ordering |
| `show-sql` | `false` | `hibernate.show_sql` |
| `tx.default-timeout-seconds` | `30` | `TransactionTemplate` timeout |
| `jpa.auditing-enabled` | `true` | Enables `@EnableJpaAuditing` |
| `hikari.maximum-pool-size` / `minimum-idle` | `10` / `2` | Pool sizing |
| `hikari.connection-timeout-ms` / `idle-timeout-ms` / `max-lifetime-ms` | `30000` / `600000` / `1800000` | Pool timeouts |
| `hikari.leak-detection-threshold-ms` / `validation-timeout-ms` / `keepalive-time-ms` | `60000` / `5000` / `300000` | Pool diagnostics |

## Depends on
`common-core` (MDC `userId` for auditor), Spring Data JPA, Hibernate, HikariCP.
