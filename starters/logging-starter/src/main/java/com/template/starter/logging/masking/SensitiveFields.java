package com.template.starter.logging.masking;

import java.util.Set;
import java.util.TreeSet;

/**
 * Shared constants for sensitive field detection used by both plaintext and JSON masking.
 *
 * <p>Centralizes the list of field names considered sensitive so that
 * {@link SensitiveDataMaskingConverter} (plaintext) and {@link MaskingJsonGeneratorDecorator}
 * (JSON) stay in sync.</p>
 */
public final class SensitiveFields {

    private SensitiveFields() {}

    public static final String REPLACEMENT = "***MASKED***";

    private static final Set<String> FIELD_NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        FIELD_NAMES.addAll(Set.of(
                "password", "passwd", "secret", "token",
                "authorization", "auth_token", "access_token", "refresh_token",
                "credit_card", "creditCard", "creditcard",
                "ssn", "api_key", "apiKey", "apikey",
                "private_key", "privateKey"
        ));
    }

    /**
     * Returns an unmodifiable view of the sensitive field names (case-insensitive lookup).
     */
    public static Set<String> fieldNames() {
        return Set.copyOf(FIELD_NAMES);
    }

    /**
     * Checks whether the given field name is sensitive (case-insensitive).
     */
    public static boolean isSensitive(String fieldName) {
        return fieldName != null && FIELD_NAMES.contains(fieldName);
    }
}
