package com.template.starter.audit;

import com.template.starter.audit.property.AuditProperties;
import com.template.starter.audit.service.AuditQueryService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.envers.AuditReader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(AuditReader.class)
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "acme.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackageClasses = AuditStarterMarker.class)
public class AuditAutoConfiguration {

    /**
     * Customizes Hibernate properties to configure Envers behavior.
     *
     * <p>Maps {@link AuditProperties} to Hibernate Envers settings:
     * audit table suffix, revision table name, store-data-at-delete, modified flags.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "enversHibernatePropertiesCustomizer")
    HibernatePropertiesCustomizer enversHibernatePropertiesCustomizer(AuditProperties props) {
        return hibernateProps -> {
            hibernateProps.put("org.hibernate.envers.audit_table_suffix", props.getAuditTableSuffix());
            hibernateProps.put("org.hibernate.envers.revision_table_name", props.getRevisionTableName());
            hibernateProps.put("org.hibernate.envers.store_data_at_delete", String.valueOf(props.isStoreDataAtDelete()));
            hibernateProps.put("org.hibernate.envers.global_with_modified_flag", String.valueOf(props.isModifiedFlags()));
        };
    }

    /**
     * Provides a convenience service for querying audit history.
     *
     * <p>Wraps the Envers {@link AuditReader} with type-safe methods
     * for retrieving entity history, revisions, and point-in-time snapshots.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EntityManagerFactory.class)
    AuditQueryService auditQueryService(EntityManagerFactory emf) {
        return new AuditQueryService(SharedEntityManagerCreator.createSharedEntityManager(emf));
    }
}
