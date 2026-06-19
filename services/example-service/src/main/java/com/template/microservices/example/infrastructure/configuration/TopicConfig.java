package com.template.microservices.example.infrastructure.configuration;

import com.template.kafka.property.KafkaMessagingProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;

import java.util.function.Function;

@org.springframework.context.annotation.Configuration
public class TopicConfig {
    @Bean
    NewTopic ordersCreated(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("orders.created");
    }

    @Bean
    NewTopic ordersCreatedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("orders.created" + mp.getDltSuffix());
    }

    // ---- Stock-reservation saga topics (shared contract with inventory-service) ----

    @Bean
    NewTopic stockReservationRequested(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("stock.reservation.requested");
    }

    @Bean
    NewTopic stockReservationRequestedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("stock.reservation.requested" + mp.getDltSuffix());
    }

    @Bean
    NewTopic stockReserved(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("stock.reserved");
    }

    @Bean
    NewTopic stockReservedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("stock.reserved" + mp.getDltSuffix());
    }

    @Bean
    NewTopic stockReservationFailed(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("stock.reservation.failed");
    }

    @Bean
    NewTopic stockReservationFailedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("stock.reservation.failed" + mp.getDltSuffix());
    }

    @Bean
    NewTopic stockReleaseRequested(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("stock.release.requested");
    }

    @Bean
    NewTopic stockReleaseRequestedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("stock.release.requested" + mp.getDltSuffix());
    }

    @Bean
    NewTopic stockReleased(Function<String, NewTopic> topicNamer) {
        return topicNamer.apply("stock.released");
    }

    @Bean
    NewTopic stockReleasedDLT(Function<String, NewTopic> topicNamer, KafkaMessagingProperties mp) {
        return topicNamer.apply("stock.released" + mp.getDltSuffix());
    }
}
