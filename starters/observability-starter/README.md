# observability-starter

Micrometer common tags, HTTP URI cardinality guard, trace-id response header. Namespace `acme.obs.*`. Feature tier. Package `com.template.observer`.

## Beans / key types
| Type | Role |
|------|------|
| `ObservabilityAutoConfiguration` | on `MeterRegistry` classpath |
| `commonTags` (`MeterRegistryCustomizer`) | tags `app` (`spring.application.name`), `env` (`APP_ENV`/active profile/`dev`), `version` (`BuildProperties`/`local`) + `common-tags` |
| `httpUriCardinalityGuard` (`MeterFilter`) | `maximumAllowableTags("http.server.requests","uri",max,deny())` |
| `traceIdResponseHeaderFilter` (`Filter`) | sets `Trace-Id` from `Tracer.currentSpan()`; on `Tracer` classpath + `add-trace-response-header` |
| `ObservabilityProperties` | bound config (nested `Tracing`) |

Bundled `application.yml` adds Actuator/tracing/log-pattern defaults.

## Config (`acme.obs.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `add-trace-response-header` | `true` | register trace-id filter |
| `max-unique-http-uris` | `200` | uri cardinality cap |
| `common-tags` | `{}` | extra static meter tags |
| `tracing.enabled` | `true` | tracing toggle |
| `tracing.probability` | `0.10` | sampling (0.0–1.0) |
| `tracing.otlp-enabled` | `false` | OTLP export |
| `tracing.otlp-endpoint` | `http://otel-collector:4317` | OTLP endpoint |

## Depends on
none (Micrometer + Actuator; Tracing optional)
