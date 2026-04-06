package com.template.starter.inbox.repository;

import com.template.starter.inbox.entity.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboxRepository extends JpaRepository<Inbox, UUID> {
    List<Inbox> findByProcessedFalse();
    Optional<Inbox> findByIdempotentToken(UUID idempotentToken);

    @Modifying
    @Query("DELETE FROM Inbox i WHERE i.processed = true AND i.receivedAt < :before")
    int deleteProcessedBefore(@Param("before") Instant before);
}
