package com.template.microservices.example.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.EventWrapper;
import com.template.microservices.example.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final ObjectMapper mapper;

    @KafkaListener(topics = "orders.created", containerFactory = "kafkaListenerContainerFactory")
    public void handle(EventWrapper<OrderCreatedEvent> evt) {
        var p = mapper.convertValue(evt.event(), OrderCreatedEvent.class);
        System.out.println("[example-service] consumed type=" + evt.type() +
                " id=" + evt.id() + " payload=" + p);
    }
}
