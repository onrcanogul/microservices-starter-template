package com.template.core.error;


/**
 * Contract for application-wide error codes.
 * We keep the HTTP status as a plain int to stay framework-agnostic.
 */
public interface ErrorCode {
    /** A stable, machine-friendly code, e.g. "validation_failed". */
    String code();

    /** HTTP status that best represents this error, e.g. 400. */
    int httpStatus();

    /** Optional i18n key helper. */
    default String messageKey() { return "error." + code(); }
}
