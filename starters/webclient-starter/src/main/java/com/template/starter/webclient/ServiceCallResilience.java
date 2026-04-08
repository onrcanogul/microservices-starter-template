package com.template.starter.webclient;

import com.template.starter.webclient.property.WebClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import java.util.function.Supplier;

/**
 * Decorates outbound service-to-service calls with Resilience4j
 * circuit breaker and retry, scoped by service name.
 * <p>
 * Each service name gets its own circuit breaker and retry instance,
 * allowing per-service resilience tuning via standard Resilience4j config:
 * <pre>{@code
 * resilience4j.circuitbreaker.instances.restclient-payment-service:
 *   slidingWindowSize: 10
 *   failureRateThreshold: 50
 * }</pre>
 *
 * <p>Usage:
 * <pre>{@code
 * @Autowired ServiceCallResilience resilience;
 * @Autowired RestClient.Builder restClientBuilder;
 *
 * RestClient paymentClient = restClientBuilder.baseUrl("http://payment-service").build();
 *
 * PaymentResponse resp = resilience.execute("payment-service", () ->
 *     paymentClient.get()
 *         .uri("/api/payments/{id}", id)
 *         .retrieve()
 *         .body(PaymentResponse.class)
 * );
 * }</pre>
 */
public class ServiceCallResilience {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final WebClientProperties properties;

    public ServiceCallResilience(CircuitBreakerRegistry circuitBreakerRegistry,
                                 RetryRegistry retryRegistry,
                                 WebClientProperties properties) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.properties = properties;
    }

    /**
     * Executes the given supplier wrapped with circuit breaker + retry
     * for the specified service name.
     *
     * @param serviceName logical name of the target service (e.g., "payment-service")
     * @param supplier    the call to execute
     * @param <T>         response type
     * @return the result of the call
     */
    public <T> T execute(String serviceName, Supplier<T> supplier) {
        String cbName = properties.getResilience().getCircuitBreakerPrefix() + "-" + serviceName;
        String retryName = properties.getResilience().getRetryPrefix() + "-" + serviceName;

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cbName);
        Retry retry = retryRegistry.retry(retryName);

        // Retry wraps CircuitBreaker: retry → circuit-breaker → supplier
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(cb, supplier);
        decorated = Retry.decorateSupplier(retry, decorated);

        return decorated.get();
    }

    /**
     * Executes a void action wrapped with circuit breaker + retry.
     *
     * @param serviceName logical name of the target service
     * @param runnable    the call to execute
     */
    public void executeVoid(String serviceName, Runnable runnable) {
        execute(serviceName, () -> {
            runnable.run();
            return null;
        });
    }
}
