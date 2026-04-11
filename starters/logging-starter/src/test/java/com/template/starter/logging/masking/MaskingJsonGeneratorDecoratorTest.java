package com.template.starter.logging.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingJsonGeneratorDecoratorTest {

    private final MaskingJsonGeneratorDecorator decorator = new MaskingJsonGeneratorDecorator();
    private final ObjectMapper mapper = new ObjectMapper();

    private StringWriter writer;
    private JsonGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        writer = new StringWriter();
        JsonGenerator raw = mapper.getFactory().createGenerator(writer);
        generator = decorator.decorate(raw);
    }

    @Test
    void masksStringValueForSensitiveField() throws Exception {
        generator.writeStartObject();
        generator.writeStringField("password", "secret123");
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).contains("\"password\":\"***MASKED***\"");
        assertThat(json).doesNotContain("secret123");
    }

    @Test
    void preservesNonSensitiveStringField() throws Exception {
        generator.writeStartObject();
        generator.writeStringField("username", "admin");
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).contains("\"username\":\"admin\"");
    }

    @Test
    void caseInsensitiveFieldMatch() throws Exception {
        generator.writeStartObject();
        generator.writeStringField("PASSWORD", "secret");
        generator.writeStringField("ApiKey", "key123");
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).doesNotContain("secret");
        assertThat(json).doesNotContain("key123");
    }

    @Test
    void masksNumericValueForSensitiveField() throws Exception {
        generator.writeStartObject();
        generator.writeFieldName("ssn");
        generator.writeNumber(123456789);
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).contains("\"ssn\":\"***MASKED***\"");
        assertThat(json).doesNotContain("123456789");
    }

    @Test
    void masksLongValueForSensitiveField() throws Exception {
        generator.writeStartObject();
        generator.writeFieldName("credit_card");
        generator.writeNumber(4111111111111111L);
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).doesNotContain("4111111111111111");
    }

    @Test
    void masksBigDecimalValueForSensitiveField() throws Exception {
        generator.writeStartObject();
        generator.writeFieldName("secret");
        generator.writeNumber(new BigDecimal("99.99"));
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).doesNotContain("99.99");
        assertThat(json).contains("***MASKED***");
    }

    @Test
    void masksBooleanValueForSensitiveField() throws Exception {
        generator.writeStartObject();
        generator.writeFieldName("token");
        generator.writeBoolean(true);
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).doesNotContain("true");
        assertThat(json).contains("\"token\":\"***MASKED***\"");
    }

    @Test
    void preservesNonSensitiveNumericField() throws Exception {
        generator.writeStartObject();
        generator.writeFieldName("orderId");
        generator.writeNumber(42);
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).contains("\"orderId\":42");
    }

    @Test
    void handlesMultipleFieldsInSequence() throws Exception {
        generator.writeStartObject();
        generator.writeStringField("username", "admin");
        generator.writeStringField("password", "secret123");
        generator.writeStringField("email", "admin@test.com");
        generator.writeStringField("token", "jwt-abc-123");
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).contains("\"username\":\"admin\"");
        assertThat(json).contains("\"password\":\"***MASKED***\"");
        assertThat(json).contains("\"email\":\"admin@test.com\"");
        assertThat(json).contains("\"token\":\"***MASKED***\"");
    }

    @Test
    void masksCharArrayString() throws Exception {
        MaskingJsonGeneratorDecorator.MaskingJsonGenerator masking =
                (MaskingJsonGeneratorDecorator.MaskingJsonGenerator) generator;

        generator.writeStartObject();
        generator.writeFieldName("password");
        char[] chars = "secret".toCharArray();
        masking.writeString(chars, 0, chars.length);
        generator.writeEndObject();
        generator.flush();

        String json = writer.toString();
        assertThat(json).doesNotContain("secret");
        assertThat(json).contains("***MASKED***");
    }
}
