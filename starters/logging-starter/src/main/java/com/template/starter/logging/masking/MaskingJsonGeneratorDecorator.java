package com.template.starter.logging.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.decorate.JsonGeneratorDecorator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Logstash encoder decorator that masks sensitive JSON field values during serialization.
 *
 * <p>When the encoder writes a JSON field whose name matches a sensitive pattern,
 * the value is replaced with {@code ***MASKED***}. This catches sensitive data
 * in structured JSON log output at the encoder level, before it reaches any appender.</p>
 */
public class MaskingJsonGeneratorDecorator implements JsonGeneratorDecorator {

    @Override
    public JsonGenerator decorate(JsonGenerator generator) {
        return new MaskingJsonGenerator(generator);
    }

    /**
     * Delegating JsonGenerator that intercepts value-write calls when the current
     * field name matches a sensitive pattern.
     */
    static class MaskingJsonGenerator extends com.fasterxml.jackson.core.util.JsonGeneratorDelegate {

        private String currentFieldName;

        MaskingJsonGenerator(JsonGenerator delegate) {
            super(delegate, true);
        }

        @Override
        public void writeFieldName(String name) throws IOException {
            this.currentFieldName = name;
            super.writeFieldName(name);
        }

        private boolean shouldMask() {
            return currentFieldName != null && SensitiveFields.isSensitive(currentFieldName);
        }

        private void resetFieldName() {
            this.currentFieldName = null;
        }

        @Override
        public void writeString(String text) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeString(text);
            }
            resetFieldName();
        }

        @Override
        public void writeString(char[] text, int offset, int len) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeString(text, offset, len);
            }
            resetFieldName();
        }

        @Override
        public void writeNumber(int v) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeNumber(v);
            }
            resetFieldName();
        }

        @Override
        public void writeNumber(long v) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeNumber(v);
            }
            resetFieldName();
        }

        @Override
        public void writeNumber(BigDecimal v) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeNumber(v);
            }
            resetFieldName();
        }

        @Override
        public void writeNumber(BigInteger v) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeNumber(v);
            }
            resetFieldName();
        }

        @Override
        public void writeBoolean(boolean state) throws IOException {
            if (shouldMask()) {
                super.writeString(SensitiveFields.REPLACEMENT);
            } else {
                super.writeBoolean(state);
            }
            resetFieldName();
        }
    }
}
