package com.template.starter.outbox.repository;

import com.template.starter.outbox.entity.Outbox;
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
    List<Outbox> findByPublishedFalse();
    List<Outbox> findTop100ByPublishedFalse();

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.published = true AND o.createdAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
