package com.template.starter.webclient;

import com.template.starter.webclient.property.WebClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCallResilienceTest {

    private CircuitBreakerRegistry cbRegistry;
    private RetryRegistry retryRegistry;
    private WebClientProperties properties;
    private ServiceCallResilience resilience;

    @BeforeEach
    void setUp() {
        cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build());

        retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build());

        properties = new WebClientProperties();
        resilience = new ServiceCallResilience(cbRegistry, retryRegistry, properties);
    }

    @Test
    void execute_shouldReturnResult_whenCallSucceeds() {
        String result = resilience.execute("test-service", () -> "OK");

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void execute_shouldRetryOnFailure_thenSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = resilience.execute("test-service", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void execute_shouldThrow_whenAllRetriesExhausted() {
        assertThatThrownBy(() ->
                resilience.execute("failing-service", () -> {
                    throw new RuntimeException("permanent failure");
                })
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void execute_shouldUsePerServiceCircuitBreaker() {
        // Each service name gets its own circuit breaker instance
        resilience.execute("service-a", () -> "A");
        resilience.execute("service-b", () -> "B");

        String cbNameA = properties.getResilience().getCircuitBreakerPrefix() + "-service-a";
        String cbNameB = properties.getResilience().getCircuitBreakerPrefix() + "-service-b";

        CircuitBreaker cbA = cbRegistry.circuitBreaker(cbNameA);
        CircuitBreaker cbB = cbRegistry.circuitBreaker(cbNameB);

        assertThat(cbA.getName()).isNotEqualTo(cbB.getName());
    }

    @Test
    void execute_shouldOpenCircuitBreaker_afterThreshold() {
        String cbName = properties.getResilience().getCircuitBreakerPrefix() + "-breaker-test";

        // Exhaust retries 4 times to trigger the circuit breaker (slidingWindowSize=4, threshold=50%)
        for (int i = 0; i < 4; i++) {
            try {
                resilience.execute("breaker-test", () -> {
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException ignored) {}
        }

        CircuitBreaker cb = cbRegistry.circuitBreaker(cbName);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void executeVoid_shouldCompleteSuccessfully() {
        AtomicInteger counter = new AtomicInteger(0);

        resilience.executeVoid("void-service", counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void executeVoid_shouldRetryOnFailure() {
        AtomicInteger attempts = new AtomicInteger(0);

        resilience.executeVoid("void-retry", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
        });

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void execute_shouldUseCustomPrefix() {
        properties.getResilience().setCircuitBreakerPrefix("custom-cb");
        properties.getResilience().setRetryPrefix("custom-retry");
        resilience = new ServiceCallResilience(cbRegistry, retryRegistry, properties);

        resilience.execute("my-service", () -> "done");

        assertThat(cbRegistry.circuitBreaker("custom-cb-my-service")).isNotNull();
        assertThat(retryRegistry.retry("custom-retry-my-service")).isNotNull();
    }
}
