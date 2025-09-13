# Persistence Starter

### Purpose
The **Persistence Starter** provides a standardized configuration for **Spring Data JPA + Hibernate**.  
It eliminates repetitive boilerplate in each service by centralizing:

- Hibernate defaults (timezone, batching, SQL logging)
- Transaction template configuration
- JPA auditing setup

This ensures **consistent persistence behavior** across all microservices.

---

### How It Works
1. **HibernatePropertiesCustomizer** → applies global Hibernate defaults.
2. **TransactionTemplate** → exposes a bean with service-wide default timeout.
3. **JpaAuditingConfig** → enables auditing if configured.

By including this starter, each service automatically inherits consistent JPA/Hibernate behavior.

---

### AutoConfiguration
[`PersistenceAutoConfiguration`](src/main/java/com/template/persistence/PersistenceAutoConfiguration.java) configures the persistence layer:

```java
@AutoConfiguration
@EnableConfigurationProperties(PersistenceProperties.class)
public class PersistenceAutoConfiguration {

    @Bean
    HibernatePropertiesCustomizer hibernateDefaults(PersistenceProperties props) {
        return (hibernateProps) -> {
            hibernateProps.put("hibernate.jdbc.time_zone", props.getTimeZone());
            hibernateProps.put("hibernate.order_inserts", props.isOrderInserts());
            hibernateProps.put("hibernate.order_updates", props.isOrderUpdates());
            if (props.getJdbcBatchSize() > 0) {
                hibernateProps.put("hibernate.jdbc.batch_size", props.getJdbcBatchSize());
            }
            hibernateProps.putIfAbsent("hibernate.hbm2ddl.auto", props.getDdlAuto());
            hibernateProps.put("hibernate.show_sql", props.isShowSql());
        };
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager ptm,
                                            PersistenceProperties props) {
        TransactionTemplate t = new TransactionTemplate(ptm);
        t.setTimeout(props.getTx().getDefaultTimeoutSeconds());
        return t;
    }
}
```

### Properties
```yaml
template:
  persistence:
    time-zone: UTC
    order-inserts: true
    order-updates: true
    jdbc-batch-size: 50
    ddl-auto: update
    show-sql: false
    tx:
      default-timeout-seconds: 30
    jpa:
      auditing-enabled: true
