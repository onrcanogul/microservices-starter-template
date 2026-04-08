package com.template.starter.webclient;

import com.template.starter.webclient.property.WebClientProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientPropertiesTest {

    @Test
    void defaults_shouldBeCorrect() {
        WebClientProperties props = new WebClientProperties();

        assertThat(props.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(props.getResilience().isEnabled()).isTrue();
        assertThat(props.getResilience().getCircuitBreakerPrefix()).isEqualTo("restclient");
        assertThat(props.getResilience().getRetryPrefix()).isEqualTo("restclient");
        assertThat(props.getHeaderPropagation().isEnabled()).isTrue();
    }

    @Test
    void setters_shouldOverrideDefaults() {
        WebClientProperties props = new WebClientProperties();
        props.setConnectTimeout(Duration.ofSeconds(2));
        props.setReadTimeout(Duration.ofSeconds(30));
        props.getResilience().setEnabled(false);
        props.getResilience().setCircuitBreakerPrefix("custom");
        props.getResilience().setRetryPrefix("custom-retry");
        props.getHeaderPropagation().setEnabled(false);

        assertThat(props.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(props.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.getResilience().isEnabled()).isFalse();
        assertThat(props.getResilience().getCircuitBreakerPrefix()).isEqualTo("custom");
        assertThat(props.getResilience().getRetryPrefix()).isEqualTo("custom-retry");
        assertThat(props.getHeaderPropagation().isEnabled()).isFalse();
    }
}
