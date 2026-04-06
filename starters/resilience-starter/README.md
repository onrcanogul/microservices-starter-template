# Resilience Starter

### Purpose
The **Resilience Starter** provides production-ready **Circuit Breaker**, **Retry**, **Bulkhead**, and **TimeLimiter** configuration via [Resilience4j](https://resilience4j.readme.io/).

It aggregates the required dependencies, ships enterprise defaults, integrates with Micrometer for metrics export, and adds structured logging for state transitions and retry attempts — all automatically.

---

### How It Works
1. **CircuitBreaker** — Monitors failure rate over a sliding window. Opens the circuit when the threshold is exceeded, preventing cascade failures.
2. **Retry** — Automatically retries transient failures (IOException, TimeoutException) with exponential backoff.
3. **Bulkhead** — Limits concurrent calls to an upstream dependency, isolating failures.
4. **TimeLimiter** — Enforces a maximum execution time for async calls.
5. **Structured Logging** — State transitions and retry attempts are logged via SLF4J so operators can correlate events in log aggregation systems.
6. **Metrics** — All Resilience4j metrics (circuit state, call counts, latency) are exported to Micrometer → Prometheus → Grafana.

---

### AutoConfiguration
[`ResilienceAutoConfiguration`](src/main/java/com/template/starter/resilience/ResilienceAutoConfiguration.java) adds operational logging beans on top of Resilience4j's native auto-configuration:

```java
@AutoConfiguration
@ConditionalOnClass(CircuitBreaker.class)
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceAutoConfiguration {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerLoggingConsumer() { ... }

    @Bean
    public RegistryEventConsumer<Retry> retryLoggingConsumer() { ... }
}
```

The native `resilience4j-spring-boot3` auto-configuration handles all registry, factory, and health indicator beans. This starter does **not** replace it — it augments.

---

### Default Configuration

| Pattern | Key Default | Rationale |
|---------|------------|-----------|
| CircuitBreaker | 50 % failure rate, 10-call window, 10 s open wait | Catches sustained errors without false positives |
| Retry | 3 attempts, 1 s base delay, 2× exponential backoff | Handles transient network glitches |
| Bulkhead | 25 concurrent calls, no wait queue | Prevents thread-pool exhaustion |
| TimeLimiter | 3 s timeout | Fails fast on hung dependencies |

All defaults can be overridden per-instance in service configuration:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        failure-rate-threshold: 30
        wait-duration-in-open-state:
          seconds: 30
```

---

### Configuration Properties

#### Starter Properties (`acme.resilience.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `acme.resilience.log-state-transitions` | `true` | Log CircuitBreaker CLOSED → OPEN → HALF_OPEN transitions |
| `acme.resilience.log-retry-attempts` | `true` | Log each retry attempt with failure cause |

#### Native Resilience4j Properties (`resilience4j.*`)
See [Resilience4j Spring Boot 3 docs](https://resilience4j.readme.io/docs/getting-started-3) for the full reference.

---

### Usage in a Service

**1. Add dependency:**
```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>resilience-starter</artifactId>
</dependency>
```

**2. Annotate methods:**
```java
@CircuitBreaker(name = "payment-service", fallbackMethod = "fallback")
@Retry(name = "payment-service")
@TimeLimiter(name = "payment-service")
public CompletableFuture<PaymentResult> processPayment(PaymentRequest req) {
    return CompletableFuture.supplyAsync(() -> paymentClient.charge(req));
}

private CompletableFuture<PaymentResult> fallback(PaymentRequest req, Throwable t) {
    return CompletableFuture.completedFuture(PaymentResult.unavailable());
}
```

**3. (Optional) Override defaults per instance:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        failure-rate-threshold: 30
        wait-duration-in-open-state:
          seconds: 30
  retry:
    instances:
      payment-service:
        max-attempts: 5
        ignore-exceptions:
          - com.template.core.exception.BusinessException
```

---

### Health Indicator
When `register-health-indicator: true` (default), circuit breaker states appear in `/actuator/health`:
```json
{
  "circuitBreakers": {
    "payment-service": { "state": "CLOSED", "failureRate": "12.5%" }
  }
}
```

---

### Metrics (Prometheus)
Key metrics automatically exported:
- `resilience4j_circuitbreaker_state` — current circuit state
- `resilience4j_circuitbreaker_calls_seconds` — call duration histogram
- `resilience4j_retry_calls_total` — total retries by result
- `resilience4j_bulkhead_available_concurrent_calls` — remaining capacity
