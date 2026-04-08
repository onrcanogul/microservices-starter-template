package com.template.starter.saga.repository;

import com.template.messaging.saga.SagaStatus;
import com.template.starter.saga.entity.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    List<SagaInstance> findByStatus(SagaStatus status);

    /** Find sagas that are stuck — running past their deadline. */
    @Query("SELECT s FROM SagaInstance s WHERE s.status = :status AND s.deadlineAt < :now AND s.retryCount < :maxRetries")
    List<SagaInstance> findStuckSagas(
            @Param("status") SagaStatus status,
            @Param("now") Instant now,
            @Param("maxRetries") int maxRetries);

    List<SagaInstance> findByCorrelationId(UUID correlationId);

    /** Cleanup completed/failed sagas older than the cutoff. */
    @Modifying
    @Query("DELETE FROM SagaInstance s WHERE s.status IN :statuses AND s.updatedAt < :cutoff")
    int deleteByStatusInAndUpdatedAtBefore(
            @Param("statuses") List<SagaStatus> statuses,
            @Param("cutoff") Instant cutoff);
}
