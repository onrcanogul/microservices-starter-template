# webclient-starter

Pre-configured **RestClient** and **WebClient** beans with Spring Cloud LoadBalancer integration, Resilience4j circuit breaker + retry decoration, and Micrometer trace propagation — enabling synchronous REST calls between microservices.

## Why

Kafka-based async communication is the default for inter-service messaging. However, some use cases (queries, synchronous workflows, health checks) require direct REST calls. This starter provides a production-ready HTTP client with:

- **Load-balanced** routing via Eureka service discovery
- **Circuit breaker + retry** via Resilience4j (per-service isolation)
- **Distributed trace propagation** via Micrometer ObservationRegistry
- **Security header forwarding** (`X-User-Id`, `X-User-Email`) from incoming requests

## Quick Start

Add the dependency:

```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>webclient-starter</artifactId>
</dependency>
```

Inject and use the load-balanced `RestClient.Builder`:

```java
@Service
@RequiredArgsConstructor
public class PaymentClient {

    private final RestClient.Builder restClientBuilder;
    private final ServiceCallResilience resilience; // optional — requires resilience-starter

    public PaymentResponse getPayment(String paymentId) {
        RestClient client = restClientBuilder
            .baseUrl("http://payment-service") // resolved via Eureka
            .build();

        return resilience.execute("payment-service", () ->
            client.get()
                .uri("/api/payments/{id}", paymentId)
                .retrieve()
                .body(PaymentResponse.class)
        );
    }
}
```

## Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `acme.webclient.enabled` | `true` | Enable/disable the starter (via `@ConditionalOnProperty`) |
| `acme.webclient.connect-timeout` | `5s` | Connection timeout for outbound calls |
| `acme.webclient.read-timeout` | `10s` | Read (response) timeout |
| `acme.webclient.header-propagation.enabled` | `true` | Forward `X-User-Id`/`X-User-Email` headers |
| `acme.webclient.resilience.enabled` | `true` | Enable Resilience4j circuit breaker + retry wrapping |
| `acme.webclient.resilience.circuit-breaker-prefix` | `restclient` | Prefix for circuit breaker instance names |
| `acme.webclient.resilience.retry-prefix` | `restclient` | Prefix for retry instance names |

## Resilience4j Configuration

The `ServiceCallResilience` creates per-service circuit breaker and retry instances using the naming convention `{prefix}-{serviceName}`. Configure them via standard Resilience4j properties:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      restclient-payment-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
  retry:
    instances:
      restclient-payment-service:
        maxAttempts: 3
        waitDuration: 500ms
```

## Provided Beans

| Bean | Condition | Description |
|------|-----------|-------------|
| `RestClient.Builder` (load-balanced) | Always | Pre-configured with timeouts, trace propagation, header forwarding |
| `WebClient.Builder` (load-balanced) | `spring-boot-starter-webflux` on classpath | For reactive callers |
| `ServiceCallResilience` | `resilience4j-circuitbreaker` + registries present | Circuit breaker + retry wrapper |

All beans use `@ConditionalOnMissingBean` and can be overridden by the service.

## Architecture Decision

- **RestClient over WebClient** for servlet services: Spring Boot 3.2+ `RestClient` is the modern blocking HTTP client — well-suited for this project's servlet-based services.
- **JDK 21 `HttpClient`**: Uses `JdkClientHttpRequestFactory` which wraps Java 21's `HttpClient` with built-in connection pooling — no external HTTP library needed.
- **No cross-dependency on resilience-starter**: Resilience4j support is optional and classpath-activated.
- **Per-service circuit isolation**: Each target service gets its own circuit breaker instance, preventing a single failing service from cascading to all outbound calls.

## Known Limitations

- **Header propagation on async threads**: The `X-User-Id`/`X-User-Email` propagation uses `RequestContextHolder` (ThreadLocal). Headers are **not** propagated on `@Async`, `CompletableFuture.supplyAsync()`, or virtual threads unless `RequestAttributes` are explicitly inherited. A debug-level log message is emitted when this occurs.
