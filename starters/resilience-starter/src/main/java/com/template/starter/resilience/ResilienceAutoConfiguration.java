package com.template.starter.resilience;

import com.template.starter.resilience.property.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that adds structured logging for Resilience4j events.
 * <p>
 * Resilience4j's own auto-configuration (from {@code resilience4j-spring-boot3}) handles
 * bean registration for CircuitBreaker, Retry, Bulkhead, TimeLimiter registries.
 * This configuration augments it with operational logging so that state transitions
 * and retry attempts appear in structured logs — essential for production debugging.
 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreaker.class)
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ResilienceAutoConfiguration.class);

    /**
     * Logs every CircuitBreaker state transition (CLOSED → OPEN → HALF_OPEN)
     * and individual errors recorded by the sliding window.
     */
    @Bean
    @ConditionalOnMissingBean(name = "circuitBreakerLoggingConsumer")
    @ConditionalOnProperty(prefix = "acme.resilience", name = "log-state-transitions",
            havingValue = "true", matchIfMissing = true)
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerLoggingConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                CircuitBreaker cb = event.getAddedEntry();
                cb.getEventPublisher()
                        .onStateTransition(e -> log.warn("CircuitBreaker [{}]: {} -> {}",
                                cb.getName(),
                                e.getStateTransition().getFromState(),
                                e.getStateTransition().getToState()))
                        .onError(e -> log.debug("CircuitBreaker [{}]: error recorded - {}",
                                cb.getName(),
                                e.getThrowable().getMessage()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) { /* no-op */ }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) { /* no-op */ }
        };
    }

    /**
     * Logs each retry attempt and final exhaustion so operators can identify
     * flaky downstream dependencies before they cascade into outages.
     */
    @Bean
    @ConditionalOnMissingBean(name = "retryLoggingConsumer")
    @ConditionalOnProperty(prefix = "acme.resilience", name = "log-retry-attempts",
            havingValue = "true", matchIfMissing = true)
    public RegistryEventConsumer<Retry> retryLoggingConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> event) {
                Retry retry = event.getAddedEntry();
                retry.getEventPublisher()
                        .onRetry(e -> log.warn("Retry [{}]: attempt #{} - {}",
                                retry.getName(),
                                e.getNumberOfRetryAttempts(),
                                e.getLastThrowable().getMessage()))
                        .onError(e -> log.error("Retry [{}]: all {} attempts exhausted - {}",
                                retry.getName(),
                                e.getNumberOfRetryAttempts(),
                                e.getLastThrowable().getMessage()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> event) { /* no-op */ }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> event) { /* no-op */ }
        };
    }
}
