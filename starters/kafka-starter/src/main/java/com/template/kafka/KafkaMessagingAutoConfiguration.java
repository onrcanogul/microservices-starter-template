package com.template.kafka;

import com.template.messaging.event.EventWrapper;
import com.template.core.exception.BusinessException;
import com.template.kafka.property.KafkaMessagingProperties;
import com.template.kafka.publisher.EventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.Function;

@AutoConfiguration
@EnableConfigurationProperties(KafkaMessagingProperties.class)
public class KafkaMessagingAutoConfiguration {

    /* ---------- Producer ---------- */

    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties props) {
        var cfg = props.buildProducerProperties();
        cfg.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(cfg);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        var kt = new KafkaTemplate<>(pf);
        kt.setObservationEnabled(true);
        return kt;
    }

    /* ---------- Consumer (EventEnvelope as value) ---------- */

    @Bean(name = "kafkaListenerContainerFactory")
    @ConditionalOnMissingBean
    public ConcurrentKafkaListenerContainerFactory<String, EventWrapper<?>> kafkaListenerContainerFactory(
            KafkaProperties props,
            KafkaTemplate<String, Object> template,
            KafkaMessagingProperties mp) {

        var cfg = props.buildConsumerProperties();

        // Configure JsonDeserializer for EventEnvelope
        JsonDeserializer<EventWrapper<?>> jd =
                new JsonDeserializer<>(EventWrapper.class);
        ConsumerFactory<String, EventWrapper<?>> cf =
                new DefaultKafkaConsumerFactory<>(cfg, new StringDeserializer(), jd);
        ConcurrentKafkaListenerContainerFactory<String, EventWrapper<?>> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);

        // Retry + DLT
        var recoverer = new DeadLetterPublishingRecoverer(template, (rec, ex) -> new TopicPartition(rec.topic() + mp.getDltSuffix(), rec.partition()));
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(mp.getBackoffMs(), mp.getMaxAttempts() - 1L));
        errorHandler.addNotRetryableExceptions(BusinessException.class);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    /* ---------- Small helpers ---------- */

    @Bean
    @ConditionalOnMissingBean
    public Function<String, NewTopic> topicNamer() {
        // Usage in services: topicNamer.apply("orders.created")
        return name -> new NewTopic(name, 3, (short) 1);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(KafkaTemplate<String, Object> template,
                                         org.springframework.core.env.Environment env) {
        String source = env.getProperty("spring.application.name", "app");
        return new EventPublisher(template, source);
    }
}
