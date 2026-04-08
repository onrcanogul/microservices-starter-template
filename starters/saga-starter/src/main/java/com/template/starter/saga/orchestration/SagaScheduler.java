package com.template.starter.saga.orchestration;

import com.template.messaging.saga.SagaStatus;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Periodic scheduler that detects stuck sagas (running past their deadline),
 * triggers compensation via {@link SagaOrchestrator#resumeById}, and cleans up
 * old completed/failed sagas based on retention policy.
 */
@Slf4j
public class SagaScheduler {

    private final SagaInstanceRepository sagaRepository;
    private final SagaProperties properties;
    private final SagaOrchestrator orchestrator;

    public SagaScheduler(SagaInstanceRepository sagaRepository,
                         SagaProperties properties,
                         SagaOrchestrator orchestrator) {
        this.sagaRepository = sagaRepository;
        this.properties = properties;
        this.orchestrator = orchestrator;
    }

    /**
     * Detect stuck sagas — those in RUNNING/STARTED state past their deadline.
     * RUNNING sagas are marked COMPENSATING and compensation is triggered.
     * STARTED sagas that failed before any step ran are marked FAILED.
     */
    @Scheduled(fixedDelayString = "#{@sagaProperties.schedulerRate.toMillis()}")
    @SchedulerLock(name = "saga_detectStuckSagas", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    @Transactional
    public void detectStuckSagas() {
        Instant now = Instant.now();
        List<SagaInstance> stuckRunning = sagaRepository.findStuckSagas(
                SagaStatus.RUNNING, now, properties.getMaxRetries());
        List<SagaInstance> stuckStarted = sagaRepository.findStuckSagas(
                SagaStatus.STARTED, now, properties.getMaxRetries());

        for (SagaInstance saga : stuckRunning) {
            log.warn("Saga [{}] type='{}' stuck in RUNNING — marking for compensation (retry {}/{})",
                    saga.getId(), saga.getSagaType(), saga.getRetryCount() + 1, properties.getMaxRetries());
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.setRetryCount(saga.getRetryCount() + 1);
            saga.setUpdatedAt(Instant.now());
            sagaRepository.save(saga);
        }

        for (SagaInstance saga : stuckStarted) {
            log.warn("Saga [{}] type='{}' stuck in STARTED — marking FAILED (retry {}/{})",
                    saga.getId(), saga.getSagaType(), saga.getRetryCount() + 1, properties.getMaxRetries());
            saga.setStatus(SagaStatus.FAILED);
            saga.setRetryCount(saga.getRetryCount() + 1);
            saga.setUpdatedAt(Instant.now());
            sagaRepository.save(saga);
        }

        // Trigger compensation for sagas that were just marked COMPENSATING
        for (SagaInstance saga : stuckRunning) {
            try {
                orchestrator.resumeById(saga.getId());
            } catch (Exception e) {
                log.error("Saga [{}] type='{}' recovery failed: {}",
                        saga.getId(), saga.getSagaType(), e.getMessage(), e);
            }
        }
    }

    /**
     * Cleanup old completed/compensated/failed sagas based on retention policy.
     */
    @Scheduled(cron = "#{@sagaProperties.cleanup.cron}")
    @SchedulerLock(name = "saga_cleanup", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(properties.getCleanup().getRetention());
        List<SagaStatus> terminalStatuses = List.of(
                SagaStatus.COMPLETED, SagaStatus.COMPENSATED, SagaStatus.FAILED);
        int deleted = sagaRepository.deleteByStatusInAndUpdatedAtBefore(terminalStatuses, cutoff);
        if (deleted > 0) {
            log.info("Saga cleanup: deleted {} terminal sagas older than {}",
                    deleted, properties.getCleanup().getRetention());
        }
    }
}
