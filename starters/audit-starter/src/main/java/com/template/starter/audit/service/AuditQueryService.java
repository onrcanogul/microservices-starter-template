package com.template.starter.audit.service;

import com.template.starter.audit.entity.CustomRevisionEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;

import org.hibernate.envers.exception.RevisionDoesNotExistException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Convenience wrapper around Hibernate Envers {@link AuditReader}.
 *
 * <p>Provides common audit trail queries without requiring services
 * to work directly with the Envers API.</p>
 */
@Transactional(readOnly = true)
public class AuditQueryService {

    private final EntityManager entityManager;

    public AuditQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Returns all historical revisions of an entity, ordered by revision number ascending.
     *
     * @param entityClass the audited entity class
     * @param entityId    the entity primary key
     * @return list of {@link AuditRevision} containing entity state, revision metadata, and operation type
     */
    @SuppressWarnings("unchecked")
    public <T> List<AuditRevision<T>> getHistory(Class<T> entityClass, Object entityId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return results.stream()
                .map(row -> new AuditRevision<>(
                        (T) row[0],
                        (CustomRevisionEntity) row[1],
                        (RevisionType) row[2]))
                .toList();
    }

    /**
     * Returns the entity state at a specific revision.
     *
     * @param entityClass the audited entity class
     * @param entityId    the entity primary key
     * @param revision    the revision number
     * @return the entity state at the given revision, or null if not found
     */
    public <T> T getAtRevision(Class<T> entityClass, Object entityId, long revision) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        return reader.find(entityClass, entityId, revision);
    }

    /**
     * Returns all revision numbers for a given entity.
     *
     * @param entityClass the audited entity class
     * @param entityId    the entity primary key
     * @return list of revision numbers in ascending order
     */
    public List<Number> getRevisions(Class<?> entityClass, Object entityId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        return reader.getRevisions(entityClass, entityId);
    }

    /**
     * Returns the revision metadata for a specific revision number.
     *
     * @param revision the revision number
     * @return the custom revision entity with user context
     */
    public CustomRevisionEntity getRevisionEntity(long revision) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        return reader.findRevision(CustomRevisionEntity.class, revision);
    }

    /**
     * Returns the entity state at a given point in time.
     *
     * @param entityClass the audited entity class
     * @param entityId    the entity primary key
     * @param at          the point in time to query
     * @return the entity state at the given point in time, or null if no revision exists before that time
     */
    public <T> T getAtPointInTime(Class<T> entityClass, Object entityId, Instant at) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        try {
            Number revision = reader.getRevisionNumberForDate(java.util.Date.from(at));
            return reader.find(entityClass, entityId, revision);
        } catch (RevisionDoesNotExistException e) {
            return null;
        }
    }

    /**
     * Holds an entity snapshot, its revision metadata, and the operation type.
     *
     * @param entity   the entity state at the revision
     * @param revision the revision metadata (user, timestamp, correlationId)
     * @param type     ADD, MOD, or DEL
     */
    public record AuditRevision<T>(
            T entity,
            CustomRevisionEntity revision,
            RevisionType type
    ) {}
}
