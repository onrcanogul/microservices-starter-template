package com.template.starter.saga;

import com.template.messaging.saga.SagaStepHandler;
import com.template.starter.saga.choreography.SagaRollbackRegistry;
import com.template.starter.saga.orchestration.SagaDefinition;
import com.template.starter.saga.orchestration.SagaOrchestrator;
import com.template.starter.saga.orchestration.SagaScheduler;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Auto-configuration for the saga-starter.
 * <p>
 * Provides:
 * <ul>
 *   <li><b>Orchestration</b>: {@link SagaOrchestrator} for driving multi-step sagas with persistent state</li>
 *   <li><b>Choreography</b>: {@link SagaRollbackRegistry} for runtime processing of {@code @SagaRollback}</li>
 *   <li><b>Recovery</b>: {@link SagaScheduler} for detecting stuck sagas and triggering compensation</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(SagaStepHandler.class)
@EnableScheduling
@EnableJpaRepositories(basePackageClasses = SagaStarterMarker.class)
@EntityScan(basePackageClasses = SagaStarterMarker.class)
@EnableConfigurationProperties(SagaProperties.class)
@ConditionalOnProperty(prefix = "acme.saga", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate sagaTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaOrchestrator sagaOrchestrator(SagaInstanceRepository sagaRepository,
                                              ObjectMapper objectMapper,
                                              SagaProperties properties,
                                              TransactionTemplate sagaTransactionTemplate) {
        return new SagaOrchestrator(sagaRepository, objectMapper, properties, sagaTransactionTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaScheduler sagaScheduler(SagaInstanceRepository sagaRepository,
                                        SagaProperties properties,
                                        SagaOrchestrator sagaOrchestrator) {
        return new SagaScheduler(sagaRepository, properties, sagaOrchestrator);
    }

    /**
     * Alias bean for SpEL expression in {@code @Scheduled} annotations.
     */
    @Bean("sagaProperties")
    @ConditionalOnMissingBean(name = "sagaProperties")
    public SagaProperties sagaPropertiesBean(SagaProperties properties) {
        return properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaRollbackRegistry sagaRollbackRegistry(ApplicationContext applicationContext) {
        return new SagaRollbackRegistry(applicationContext);
    }

    /**
     * Auto-register all {@link SagaDefinition} beans with the orchestrator at startup,
     * enabling recovery via {@link SagaOrchestrator#resumeById}.
     */
    @Bean
    SmartInitializingSingleton sagaDefinitionRegistrar(SagaOrchestrator orchestrator,
                                                       ObjectProvider<SagaDefinition<?>> definitions) {
        return () -> definitions.forEach(orchestrator::register);
    }
}
