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
import java.util.ArrayList;
import java.util.List;

/**
 * Periodic scheduler that detects stuck sagas (running or awaiting a reply past their deadline),
 * triggers compensation via {@link SagaOrchestrator#resumeById}, and cleans up old
 * completed/failed sagas based on retention policy.
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
     * Detect stuck sagas — those past their deadline.
     * <ul>
     *   <li>RUNNING and WAITING_FOR_REPLY → marked COMPENSATING; compensation is triggered.
     *       For WAITING_FOR_REPLY this only compensates — the suspended async step's {@code execute()}
     *       is never re-run, so the request is not re-published.</li>
     *   <li>STARTED (failed before any step ran) → marked FAILED.</li>
     * </ul>
     */
    @Scheduled(fixedDelayString = "#{@sagaProperties.schedulerRate.toMillis()}")
    @SchedulerLock(name = "saga_detectStuckSagas", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    @Transactional
    public void detectStuckSagas() {
        Instant now = Instant.now();
        List<SagaInstance> stuckRunning = sagaRepository.findStuckSagas(
                SagaStatus.RUNNING, now, properties.getMaxRetries());
        List<SagaInstance> stuckWaiting = sagaRepository.findStuckSagas(
                SagaStatus.WAITING_FOR_REPLY, now, properties.getMaxRetries());
        List<SagaInstance> stuckStarted = sagaRepository.findStuckSagas(
                SagaStatus.STARTED, now, properties.getMaxRetries());

        // RUNNING / WAITING_FOR_REPLY → compensate. They keep partial progress that must be undone.
        List<SagaInstance> toCompensate = new ArrayList<>();
        toCompensate.addAll(stuckRunning);
        toCompensate.addAll(stuckWaiting);

        for (SagaInstance saga : toCompensate) {
            log.warn("Saga [{}] type='{}' stuck in {} — marking for compensation (retry {}/{})",
                    saga.getId(), saga.getSagaType(), saga.getStatus(),
                    saga.getRetryCount() + 1, properties.getMaxRetries());
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

        // Trigger compensation. resumeById -> resume() sees COMPENSATING -> compensate() only;
        // it never re-runs a suspended step's execute().
        for (SagaInstance saga : toCompensate) {
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
