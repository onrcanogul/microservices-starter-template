package com.template.core.audit;

import java.time.Instant;

/**
 * Marker + contract for insert auditing.
 * Entities implementing this must expose createdAt/createdBy fields.
 */
public interface IInsertAuditing {
    Instant getCreatedAt();
    void setCreatedAt(Instant createdAt);

    String getCreatedBy();
    void setCreatedBy(String createdBy);
}
