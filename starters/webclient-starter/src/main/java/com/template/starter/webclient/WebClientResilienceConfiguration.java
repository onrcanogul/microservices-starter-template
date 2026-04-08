package com.template.starter.webclient;

import com.template.starter.webclient.property.WebClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Conditional Resilience4j integration for the webclient-starter.
 * <p>
 * Activated only when {@code resilience4j-circuitbreaker} is on the classpath
 * and {@code acme.webclient.resilience.enabled=true} (default).
 * <p>
 * Provides a {@link ServiceCallResilience} helper that wraps synchronous
 * service-to-service calls with circuit breaker + retry.
 */
@AutoConfiguration(after = WebClientAutoConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(prefix = "acme.webclient.resilience", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class WebClientResilienceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebClientResilienceConfiguration.class);

    /**
     * Provides a {@link ServiceCallResilience} that decorates outbound calls
     * with per-service circuit breaker and retry instances.
     * <p>
     * If the application does not bring in Resilience4j's auto-configuration
     * (no {@link CircuitBreakerRegistry} or {@link RetryRegistry} beans),
     * this bean is not created.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({CircuitBreakerRegistry.class, RetryRegistry.class})
    public ServiceCallResilience serviceCallResilience(
            CircuitBreakerRegistry cbRegistry,
            RetryRegistry retryRegistry,
            WebClientProperties props) {
        log.info("webclient-starter: Resilience4j integration enabled (cbPrefix={}, retryPrefix={})",
                props.getResilience().getCircuitBreakerPrefix(),
                props.getResilience().getRetryPrefix());
        return new ServiceCallResilience(cbRegistry, retryRegistry, props);
    }
}
