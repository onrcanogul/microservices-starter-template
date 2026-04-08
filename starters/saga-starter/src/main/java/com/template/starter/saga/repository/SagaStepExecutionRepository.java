package com.template.starter.saga.repository;

import com.template.starter.saga.entity.SagaStepExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaStepExecutionRepository extends JpaRepository<SagaStepExecution, UUID> {

    List<SagaStepExecution> findBySagaInstanceIdOrderByStepOrderAsc(UUID sagaInstanceId);
}
