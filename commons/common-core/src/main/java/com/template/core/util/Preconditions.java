package com.template.core.util;

/**
 * Minimal precondition checks (avoid pulling big utility libs).
 */
public final class Preconditions {
    private Preconditions() {}

    /** Ensures the reference is not null; otherwise throws IllegalArgumentException. */
    public static <T> T checkNotNull(T ref, String message) {
        if (ref == null) throw new IllegalArgumentException(message);
        return ref;
    }

    /** Ensures the expression is true; otherwise throws IllegalArgumentException. */
    public static void checkArgument(boolean expression, String message) {
        if (!expression) throw new IllegalArgumentException(message);
    }
}
