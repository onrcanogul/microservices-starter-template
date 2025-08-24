package com.template.core.audit;


import java.time.Instant;

/**
 * Marker + contract for soft delete semantics.
 * Entities implementing this must expose deleted flags/metadata.
 */
public interface ISoftDelete {
    boolean isDeleted();
    void setDeleted(boolean deleted);

    Instant getDeletedAt();
    void setDeletedAt(Instant deletedAt);

    String getDeletedBy();
    void setDeletedBy(String deletedBy);
}
