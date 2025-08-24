package com.template.core.audit;

import java.time.Instant;

/**
 * Marker + contract for update auditing.
 * Entities implementing this must expose updatedAt/updatedBy fields.
 */
public interface IUpdateAuditing {
    Instant getUpdatedAt();
    void setUpdatedAt(Instant updatedAt);

    String getUpdatedBy();
    void setUpdatedBy(String updatedBy);
}
