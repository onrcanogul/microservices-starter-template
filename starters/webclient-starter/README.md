# webclient-starter

Load-balanced `RestClient`/`WebClient` builders with header propagation + optional Resilience4j. Namespace `acme.webclient.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `WebClientAutoConfiguration` | on `RestClient` classpath |
| `RestClient.Builder` (`@LoadBalanced`) | JDK 21 `HttpClient` factory, timeouts, `ObservationRegistry` trace, `X-User-Id`/`X-User-Email` interceptor |
| `WebClient.Builder` (`@LoadBalanced`) | reactive variant, on `WebClient` classpath |
| `WebClientResilienceConfiguration` | on `CircuitBreaker` classpath + `acme.webclient.resilience.enabled` |
| `ServiceCallResilience` | per-service CB+retry wrapper; instance names `{prefix}-{serviceName}`; needs `CircuitBreakerRegistry`+`RetryRegistry` beans |
| `WebClientProperties` | bound config (nested `Resilience`, `HeaderPropagation`) |

Header propagation uses `RequestContextHolder` (ThreadLocal); skipped on async/virtual threads.

## Config (`acme.webclient.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | activate starter |
| `connect-timeout` | `5s` | connect timeout |
| `read-timeout` | `10s` | read timeout |
| `header-propagation.enabled` | `true` | forward user headers |
| `resilience.enabled` | `true` | wrap calls with CB+retry |
| `resilience.circuit-breaker-prefix` | `restclient` | CB instance name prefix |
| `resilience.retry-prefix` | `restclient` | retry instance name prefix |

## Depends on
none (Resilience4j optional, classpath-activated)
