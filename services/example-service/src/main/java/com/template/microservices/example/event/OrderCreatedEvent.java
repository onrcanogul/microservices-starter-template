package com.template.microservices.example.event;

// move into common
public record OrderCreatedEvent(Long orderId, String sku, Integer amount){

}
