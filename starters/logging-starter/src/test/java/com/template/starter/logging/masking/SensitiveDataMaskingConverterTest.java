package com.template.starter.logging.masking;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SensitiveDataMaskingConverterTest {

    private final SensitiveDataMaskingConverter converter = new SensitiveDataMaskingConverter();

    @Test
    void transform_masksJsonPasswordField() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "User login: {\"username\":\"admin\",\"password\":\"secret123\"}";

        String result = converter.transform(event, input);

        assertThat(result).contains("\"username\":\"admin\"");
        assertThat(result).contains("\"***MASKED***\"");
        assertThat(result).doesNotContain("secret123");
    }

    @Test
    void transform_masksJsonTokenField() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9.abc.def\",\"userId\":\"123\"}";

        String result = converter.transform(event, input);

        assertThat(result).contains("\"***MASKED***\"");
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(result).contains("\"userId\":\"123\"");
    }

    @Test
    void transform_masksKeyValueFormat() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "Config loaded: password=mysecret, host=localhost";

        String result = converter.transform(event, input);

        assertThat(result).contains("password=***MASKED***");
        assertThat(result).doesNotContain("mysecret");
        assertThat(result).contains("host=localhost");
    }

    @Test
    void transform_masksMultipleSensitiveFields() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "{\"password\":\"pwd1\",\"apiKey\":\"key123\",\"name\":\"safe\"}";

        String result = converter.transform(event, input);

        assertThat(result).doesNotContain("pwd1");
        assertThat(result).doesNotContain("key123");
        assertThat(result).contains("\"name\":\"safe\"");
    }

    @Test
    void transform_caseInsensitiveMatching() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "{\"PASSWORD\":\"secret\",\"ApiKey\":\"key\"}";

        String result = converter.transform(event, input);

        assertThat(result).doesNotContain("secret");
        assertThat(result).doesNotContain("\"key\"");
    }

    @Test
    void transform_preservesNonSensitiveContent() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "Order processed: {\"orderId\":123,\"status\":\"COMPLETED\"}";

        String result = converter.transform(event, input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void transform_handlesNullAndEmptyStrings() {
        ILoggingEvent event = mock(ILoggingEvent.class);

        assertThat(converter.transform(event, null)).isNull();
        assertThat(converter.transform(event, "")).isEmpty();
    }

    @Test
    void transform_masksAuthorizationField() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        String input = "{\"authorization\":\"Bearer eyJhbG...\"}";

        String result = converter.transform(event, input);

        assertThat(result).doesNotContain("Bearer");
        assertThat(result).contains("\"***MASKED***\"");
    }
}
