# resilience-starter

Adds structured operational logging for Resilience4j CircuitBreaker state transitions and Retry attempts. Resilience4j's own auto-config still owns bean registration. Config under `acme.resilience.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `ResilienceAutoConfiguration` | Gated on `CircuitBreaker` on classpath; registers logging consumers |
| `ResilienceProperties` | `@ConfigurationProperties("acme.resilience")` |
| `RegistryEventConsumer<CircuitBreaker>` (`circuitBreakerLoggingConsumer`) | Logs CLOSED→OPEN→HALF_OPEN transitions + recorded errors |
| `RegistryEventConsumer<Retry>` (`retryLoggingConsumer`) | Logs each retry attempt and final exhaustion |

## Config (`acme.resilience.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `log-state-transitions` | `true` | Enable CircuitBreaker logging consumer |
| `log-retry-attempts` | `true` | Enable Retry logging consumer |

## Depends on
`resilience4j-spring-boot3`.

## See
`docs/patterns/structured-logging.md`
