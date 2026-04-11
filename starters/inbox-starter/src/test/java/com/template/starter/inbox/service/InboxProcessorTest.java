package com.template.starter.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.messaging.event.base.Event;
import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.event.version.EventVersion;
import com.template.messaging.event.version.EventUpcaster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InboxProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestEventV1(String name) implements Event {}

    @EventVersion(2)
    record TestEventV2(String name, String email) implements Event {}

    @Test
    void getType_sameVersion_noUpcasting() {
        InboxProcessor processor = createProcessor(EventUpcastChain.empty());

        String payload = "{\"name\":\"Alice\",\"email\":\"alice@test.com\"}";
        TestEventV2 result = processor.getType(payload, TestEventV2.class, 2);

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.email()).isEqualTo("alice@test.com");
    }

    @Test
    void getType_olderStoredVersion_upcastsBeforeDeserialization() {
        EventUpcaster v1ToV2 = new EventUpcaster() {
            @Override public String eventType() { return TestEventV2.class.getName(); }
            @Override public int fromVersion() { return 1; }
            @Override public int toVersion() { return 2; }
            @Override public String upcast(String jsonPayload) {
                return jsonPayload.replace("}", ",\"email\":\"default@test.com\"}");
            }
        };

        EventUpcastChain chain = new EventUpcastChain(List.of(v1ToV2));
        InboxProcessor processor = createProcessor(chain);

        String v1Payload = "{\"name\":\"Bob\"}";
        TestEventV2 result = processor.getType(v1Payload, TestEventV2.class, 1);

        assertThat(result.name()).isEqualTo("Bob");
        assertThat(result.email()).isEqualTo("default@test.com");
    }

    @Test
    void getType_newerStoredVersion_noUpcasting() {
        InboxProcessor processor = createProcessor(EventUpcastChain.empty());

        String payload = "{\"name\":\"Charlie\",\"email\":\"charlie@test.com\"}";
        // storedVersion 3 is newer than current @EventVersion(2), so no upcasting
        TestEventV2 result = processor.getType(payload, TestEventV2.class, 3);

        assertThat(result.name()).isEqualTo("Charlie");
        assertThat(result.email()).isEqualTo("charlie@test.com");
    }

    @Test
    void getType_backwardCompatibleConstructor_worksWithoutUpcastChain() {
        // Use the backward-compatible constructor (no upcast chain)
        InboxProcessor processor = new InboxProcessor(objectMapper) {
            @Override public void process() {}
        };

        String payload = "{\"name\":\"Dana\",\"email\":\"dana@test.com\"}";
        TestEventV2 result = processor.getType(payload, TestEventV2.class, 2);

        assertThat(result.name()).isEqualTo("Dana");
    }

    private InboxProcessor createProcessor(EventUpcastChain chain) {
        return new InboxProcessor(objectMapper, chain) {
            @Override
            public void process() {}
        };
    }
}
