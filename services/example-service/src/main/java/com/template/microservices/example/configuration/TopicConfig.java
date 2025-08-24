package com.template.microservices.example.configuration;

import com.template.kafka.property.KafkaMessagingProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;

import java.util.function.Function;

public class TopicConfig {
    @Bean
    NewTopic ordersCreated(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("orders.created");
    }

    @Bean
    NewTopic ordersCreatedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("orders.created" + mp.getDltSuffix());
    }
}
