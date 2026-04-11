package com.template.messaging.event.version;

import com.template.messaging.event.version.EventUpcaster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventUpcastChainTest {

    @Test
    void upcast_noUpcastersRegistered_returnsOriginalPayload() {
        EventUpcastChain chain = new EventUpcastChain(List.of());
        String result = chain.upcast("com.example.MyEvent", 1, 3, "{\"a\":1}");
        assertThat(result).isEqualTo("{\"a\":1}");
    }

    @Test
    void upcast_sameVersion_returnsOriginalPayload() {
        EventUpcastChain chain = new EventUpcastChain(List.of());
        String result = chain.upcast("com.example.MyEvent", 2, 2, "{\"a\":1}");
        assertThat(result).isEqualTo("{\"a\":1}");
    }

    @Test
    void upcast_fromVersionGreaterThanToVersion_returnsOriginalPayload() {
        EventUpcastChain chain = new EventUpcastChain(List.of());
        String result = chain.upcast("com.example.MyEvent", 5, 2, "{\"a\":1}");
        assertThat(result).isEqualTo("{\"a\":1}");
    }

    @Test
    void upcast_singleHop_transformsPayload() {
        EventUpcaster v1ToV2 = new TestUpcaster("com.example.MyEvent", 1, 2,
                json -> json.replace("}", ",\"newField\":\"default\"}"));

        EventUpcastChain chain = new EventUpcastChain(List.of(v1ToV2));
        String result = chain.upcast("com.example.MyEvent", 1, 2, "{\"a\":1}");
        assertThat(result).isEqualTo("{\"a\":1,\"newField\":\"default\"}");
    }

    @Test
    void upcast_multipleHops_chainsTransformationsInOrder() {
        EventUpcaster v1ToV2 = new TestUpcaster("com.example.MyEvent", 1, 2,
                json -> json + ":v2");
        EventUpcaster v2ToV3 = new TestUpcaster("com.example.MyEvent", 2, 3,
                json -> json + ":v3");
        EventUpcaster v3ToV4 = new TestUpcaster("com.example.MyEvent", 3, 4,
                json -> json + ":v4");

        EventUpcastChain chain = new EventUpcastChain(List.of(v1ToV2, v2ToV3, v3ToV4));
        String result = chain.upcast("com.example.MyEvent", 1, 4, "base");
        assertThat(result).isEqualTo("base:v2:v3:v4");
    }

    @Test
    void upcast_partialChain_onlyAppliesRangeRequested() {
        EventUpcaster v1ToV2 = new TestUpcaster("com.example.MyEvent", 1, 2, json -> json + ":v2");
        EventUpcaster v2ToV3 = new TestUpcaster("com.example.MyEvent", 2, 3, json -> json + ":v3");

        EventUpcastChain chain = new EventUpcastChain(List.of(v1ToV2, v2ToV3));
        String result = chain.upcast("com.example.MyEvent", 2, 3, "base");
        assertThat(result).isEqualTo("base:v3");
    }

    @Test
    void upcast_missingIntermediateUpcaster_throwsIllegalState() {
        EventUpcaster v1ToV2 = new TestUpcaster("com.example.MyEvent", 1, 2, json -> json + ":v2");
        // No v2→v3 upcaster

        EventUpcastChain chain = new EventUpcastChain(List.of(v1ToV2));
        assertThatThrownBy(() -> chain.upcast("com.example.MyEvent", 1, 3, "base"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing upcaster")
                .hasMessageContaining("from version 2 to 3");
    }

    @Test
    void upcast_differentEventTypes_appliesCorrectUpcaster() {
        EventUpcaster orderV1ToV2 = new TestUpcaster("Order", 1, 2, json -> json + ":order-v2");
        EventUpcaster paymentV1ToV2 = new TestUpcaster("Payment", 1, 2, json -> json + ":payment-v2");

        EventUpcastChain chain = new EventUpcastChain(List.of(orderV1ToV2, paymentV1ToV2));

        assertThat(chain.upcast("Order", 1, 2, "data")).isEqualTo("data:order-v2");
        assertThat(chain.upcast("Payment", 1, 2, "data")).isEqualTo("data:payment-v2");
    }

    @Test
    void hasUpcastersFor_returnsTrue_whenRegistered() {
        EventUpcaster upcaster = new TestUpcaster("com.example.MyEvent", 1, 2, json -> json);
        EventUpcastChain chain = new EventUpcastChain(List.of(upcaster));

        assertThat(chain.hasUpcastersFor("com.example.MyEvent")).isTrue();
        assertThat(chain.hasUpcastersFor("com.example.OtherEvent")).isFalse();
    }

    @Test
    void empty_returnsChainThatDoesNothing() {
        EventUpcastChain chain = EventUpcastChain.empty();
        String result = chain.upcast("any.Event", 1, 5, "payload");
        assertThat(result).isEqualTo("payload");
    }

    @Test
    void constructor_duplicateUpcaster_throwsIllegalArgument() {
        EventUpcaster first = new TestUpcaster("com.example.MyEvent", 1, 2, json -> json + ":first");
        EventUpcaster duplicate = new TestUpcaster("com.example.MyEvent", 1, 2, json -> json + ":dup");

        assertThatThrownBy(() -> new EventUpcastChain(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate upcaster")
                .hasMessageContaining("fromVersion=1");
    }

    @Test
    void constructor_invalidToVersion_throwsIllegalArgument() {
        EventUpcaster bad = new TestUpcaster("com.example.MyEvent", 1, 3, json -> json);

        assertThatThrownBy(() -> new EventUpcastChain(List.of(bad)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toVersion == fromVersion + 1");
    }

    // --- helper ---

    private record TestUpcaster(String eventType, int fromVersion, int toVersion,
                                java.util.function.Function<String, String> fn) implements EventUpcaster {
        @Override
        public String upcast(String jsonPayload) {
            return fn.apply(jsonPayload);
        }
    }
}
