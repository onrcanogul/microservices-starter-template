package com.template.messaging.event.version;

import com.template.messaging.event.base.Event;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventVersionUtilTest {

    @Test
    void getVersion_withAnnotation_returnsAnnotatedValue() {
        assertThat(EventVersionUtil.getVersion(VersionedEvent.class)).isEqualTo(3);
    }

    @Test
    void getVersion_withoutAnnotation_returnsDefaultOne() {
        assertThat(EventVersionUtil.getVersion(UnversionedEvent.class)).isEqualTo(1);
    }

    @Test
    void getVersion_withDefaultAnnotation_returnsOne() {
        assertThat(EventVersionUtil.getVersion(DefaultVersionEvent.class)).isEqualTo(1);
    }

    // --- test events ---

    @EventVersion(3)
    record VersionedEvent(String data) implements Event {}

    record UnversionedEvent(String data) implements Event {}

    @EventVersion
    record DefaultVersionEvent(String data) implements Event {}
}
