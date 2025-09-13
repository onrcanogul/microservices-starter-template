package com.template.persistence;

import com.template.persistence.property.PersistenceProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(EntityManagerFactoryBuilder.class)
@EnableConfigurationProperties(PersistenceProperties.class)
public class PersistenceAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "org.hibernate.engine.spi.SessionFactoryImplementor")
    HibernatePropertiesCustomizer hibernateDefaults(PersistenceProperties props) {
        return (Map<String, Object> hibernateProps) -> {
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
    @ConditionalOnMissingBean
    @ConditionalOnBean(PlatformTransactionManager.class)
    TransactionTemplate transactionTemplate(PlatformTransactionManager ptm,
                                            PersistenceProperties props) {
        TransactionTemplate t = new TransactionTemplate(ptm);
        t.setTimeout(props.getTx().getDefaultTimeoutSeconds());
        return t;
    }

    @AutoConfiguration
    @ConditionalOnClass(EnableJpaAuditing.class)
    @ConditionalOnProperty(prefix = "acme.persistence.jpa",
            name = "auditing-enabled", havingValue = "true", matchIfMissing = true)
    @EnableJpaAuditing
    static class JpaAuditingConfig {

        @Bean
        @ConditionalOnMissingBean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("SYSTEM");
        }
    }
}

