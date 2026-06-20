package com.template.starter.outbox.repository;

import com.template.starter.outbox.entity.Outbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    /** Rows eligible for a publish attempt: unpublished, not dead, and past their backoff. */
    @Query("SELECT o FROM Outbox o WHERE o.published = false AND o.dead = false " +
           "AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now) ORDER BY o.createdAt ASC")
    List<Outbox> findBatchToPublish(@Param("now") Instant now, Pageable pageable);

    /** Dead-lettered rows (give-up after max attempts) — for observation / replay. */
    List<Outbox> findByDeadTrue();

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.published = true AND o.createdAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
