package com.template.microservices.example.infrastructure.messaging.upcaster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreatedV1ToV2UpcasterTest {

    private OrderCreatedV1ToV2Upcaster upcaster;

    @BeforeEach
    void setUp() {
        upcaster = new OrderCreatedV1ToV2Upcaster(new ObjectMapper());
    }

    @Test
    void upcast_missingCustomerEmail_addsNullField() {
        String v1Payload = "{\"orderId\":1,\"sku\":\"ABC\",\"amount\":5}";

        String result = upcaster.upcast(v1Payload);

        assertThat(result).contains("\"customerEmail\"");
        assertThat(result).contains("\"orderId\"");
        assertThat(result).contains("\"sku\"");
        assertThat(result).contains("\"amount\"");
    }

    @Test
    void upcast_existingCustomerEmail_preservesValue() {
        String v2Payload = "{\"orderId\":1,\"sku\":\"ABC\",\"amount\":5,\"customerEmail\":\"user@test.com\"}";

        String result = upcaster.upcast(v2Payload);

        assertThat(result).contains("\"customerEmail\":\"user@test.com\"");
    }

    @Test
    void upcast_malformedJson_throwsIllegalState() {
        String badPayload = "not-json";

        assertThatThrownBy(() -> upcaster.upcast(badPayload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to upcast OrderCreatedEvent v1");
    }

    @Test
    void eventType_returnsOrderCreatedEventClassName() {
        assertThat(upcaster.eventType())
                .isEqualTo("com.template.microservices.example.infrastructure.messaging.OrderCreatedEvent");
    }

    @Test
    void versionHop_isFromOneToTwo() {
        assertThat(upcaster.fromVersion()).isEqualTo(1);
        assertThat(upcaster.toVersion()).isEqualTo(2);
    }
}
