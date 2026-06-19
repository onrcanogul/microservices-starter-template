# kafka-starter

Kafka producer/consumer wiring with JSON (de)serialization, retry + DLT, and an `EventPublisher` that emits `EventWrapper<T>` with trace/correlation headers. Config under `acme.messaging.kafka.*`. FOUNDATION tier.

## Beans / key types
| Type | Role |
|------|------|
| `KafkaMessagingAutoConfiguration` | Wires producer/consumer factories, retry/DLT, publisher |
| `KafkaMessagingProperties` | `@ConfigurationProperties("acme.messaging.kafka")` |
| `ProducerFactory<String,Object>` | String key + `JsonSerializer` value |
| `KafkaTemplate<String,Object>` | Observation-enabled template |
| `ConcurrentKafkaListenerContainerFactory<String,EventWrapper<?>>` | Consumer factory; `JsonDeserializer` for `EventWrapper`, retry + DLT |
| `DefaultErrorHandler` | `FixedBackOff(backoffMs, maxAttempts-1)`; `BusinessException` is non-retryable |
| `EventPublisher` | `publish(topic, type, payload[, headers])`; wraps in `EventWrapper<T>`, propagates MDC `correlationId`/`userId` + traceId |
| `Function<String,NewTopic>` (`topicNamer`) | Names topics: 3 partitions, replication 1 |

## Config (`acme.messaging.kafka.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `max-attempts` | `5` | Total attempts incl. first try |
| `backoff-ms` | `200` | Backoff between retries |
| `dlt-suffix` | `.DLT` | Dead-letter topic suffix |
| `trusted-packages` | `com.template` | `JsonDeserializer` trusted packages (comma-separated) |

## Depends on
`common-core` (`BusinessException`, `TraceContext`), `common-messaging` (`Event`, `EventWrapper`, `MessageHeaders`), Spring Kafka.

## See
skill `kafka-event-flow`
