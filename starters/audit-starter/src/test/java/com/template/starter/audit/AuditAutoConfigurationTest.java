package com.template.starter.audit;

import com.template.starter.audit.property.AuditProperties;
import com.template.starter.audit.service.AuditQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    AuditAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:auditdb;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
            );

    private final ApplicationContextRunner simpleRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class));

    @Test
    void autoConfiguration_registersPropertiesAndCustomizer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuditProperties.class);
            assertThat(context).hasBean("enversHibernatePropertiesCustomizer");
            assertThat(context).hasSingleBean(AuditQueryService.class);
        });
    }

    @Test
    void autoConfiguration_disabledByProperty() {
        simpleRunner
                .withPropertyValues("acme.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuditAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(AuditQueryService.class);
                });
    }

    @Test
    void properties_defaultValues() {
        contextRunner.run(context -> {
            AuditProperties props = context.getBean(AuditProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.isStoreDataAtDelete()).isTrue();
            assertThat(props.isModifiedFlags()).isFalse();
            assertThat(props.getAuditTableSuffix()).isEqualTo("_aud");
            assertThat(props.getRevisionTableName()).isEqualTo("revinfo");
        });
    }

    @Test
    void properties_customValues() {
        contextRunner
                .withPropertyValues(
                        "acme.audit.store-data-at-delete=false",
                        "acme.audit.modified-flags=true",
                        "acme.audit.audit-table-suffix=_history",
                        "acme.audit.revision-table-name=audit_revisions"
                )
                .run(context -> {
                    AuditProperties props = context.getBean(AuditProperties.class);
                    assertThat(props.isStoreDataAtDelete()).isFalse();
                    assertThat(props.isModifiedFlags()).isTrue();
                    assertThat(props.getAuditTableSuffix()).isEqualTo("_history");
                    assertThat(props.getRevisionTableName()).isEqualTo("audit_revisions");
                });
    }

    @Test
    void auditQueryService_respectsConditionalOnMissingBean() {
        simpleRunner
                .withBean(AuditQueryService.class, () -> new AuditQueryService(null))
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditQueryService.class);
                });
    }
}
