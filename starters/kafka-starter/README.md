# Kafka Starter

### Purpose of Kafka
The **Kafka Starter** provides a ready-to-use configuration for integrating **Apache Kafka** into microservices.  
It simplifies **producer/consumer setup**, enables **automatic retries with Dead Letter Topics (DLT)**, and integrates with Spring Boot’s observability stack.

This starter eliminates boilerplate Kafka configuration, so services only need to focus on **publishing and consuming domain events**.

---

### How It Works
1. **ProducerFactory & KafkaTemplate** → configured with `JsonSerializer` for automatic object → JSON conversion.
2. **ConsumerFactory & Listener Container** → configured with `JsonDeserializer` for type-safe message consumption.
3. **Error Handling** → built-in retry + Dead Letter Topic (DLT) support using `DefaultErrorHandler` and `DeadLetterPublishingRecoverer`.
4. **EventPublisher** → wrapper for consistent publishing of domain events with metadata.

---

### AutoConfiguration
[`KafkaMessagingAutoConfiguration`](src/main/java/com/template/kafka/KafkaMessagingAutoConfiguration.java) automatically registers core Kafka beans:

```java
@AutoConfiguration
@EnableConfigurationProperties(KafkaMessagingProperties.class)
public class KafkaMessagingAutoConfiguration {
    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties props) { ... }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) { ... }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, EventWrapper<?>> kafkaListenerContainerFactory(...) { ... }

    @Bean
    public Function<String, NewTopic> topicNamer() { ... }

    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, Object> template, Environment env) { ... }
}
