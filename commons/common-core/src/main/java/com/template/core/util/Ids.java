package com.template.core.util;

import java.util.UUID;

/**
 * Simple ID helpers (UUID).
 * If you need ULIDs, consider adding a separate library in your service.
 */
public final class Ids {
    private Ids() {}

    /** Returns a random UUID string (with dashes). */
    public static String uuid() { return UUID.randomUUID().toString(); }

    /** Returns a random UUID string without dashes. */
    public static String uuidNoDash() { return uuid().replace("-", ""); }
}
