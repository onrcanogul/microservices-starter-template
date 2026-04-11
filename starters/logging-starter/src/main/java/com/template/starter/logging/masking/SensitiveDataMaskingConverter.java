package com.template.starter.logging.masking;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Logback converter that masks sensitive data in log messages.
 *
 * <p>Scans formatted log message content for JSON-like key-value patterns
 * where the key matches a sensitive field name (password, token, secret, etc.)
 * and replaces the value with a masked placeholder.</p>
 *
 * <p>Registered in logback-spring.xml as a conversion rule:</p>
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg"
 *     converterClass="com.template.starter.logging.masking.SensitiveDataMaskingConverter"/>
 * }</pre>
 *
 * <p>Handles both JSON format ({@code "password":"secret123"}) and
 * key=value format ({@code password=secret123}).</p>
 */
public class SensitiveDataMaskingConverter extends CompositeConverter<ILoggingEvent> {

    /**
     * Compiled regex matching sensitive values in both JSON and key=value formats.
     * Group 1: the key + separator (preserved).
     * Group 2: the value (quoted JSON string or plain value).
     */
    private static final Pattern MASK_PATTERN;

    static {
        String keys = String.join("|", SensitiveFields.fieldNames());
        // Matches: "key":"value", "key" : "value", key=value, key = value
        String regex = "(?i)"
                + "("
                + "\"(?:" + keys + ")\"\\s*:\\s*" // JSON key
                + "|"
                + "(?:" + keys + ")\\s*=\\s*"     // key=value
                + ")"
                + "("
                + "\"[^\"]*\""                     // JSON string value
                + "|"
                + "[^,\\s}\\]]*"                   // plain value until delimiter
                + ")";
        MASK_PATTERN = Pattern.compile(regex);
    }

    @Override
    protected String transform(ILoggingEvent event, String formattedMessage) {
        if (formattedMessage == null || formattedMessage.isEmpty()) {
            return formattedMessage;
        }
        return MASK_PATTERN.matcher(formattedMessage)
                .replaceAll(mr -> {
                    boolean quoted = mr.group(2).startsWith("\"");
                    String masked = quoted
                            ? "\"" + SensitiveFields.REPLACEMENT + "\""
                            : SensitiveFields.REPLACEMENT;
                    return mr.group(1) + masked;
                });
    }
}
