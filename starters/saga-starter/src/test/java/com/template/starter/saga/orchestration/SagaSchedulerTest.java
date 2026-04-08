package com.template.starter.saga.orchestration;

import com.template.messaging.saga.SagaStatus;
import com.template.starter.saga.entity.SagaInstance;
import com.template.starter.saga.property.SagaProperties;
import com.template.starter.saga.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SagaSchedulerTest {

    private SagaInstanceRepository sagaRepository;
    private SagaOrchestrator orchestrator;
    private SagaProperties properties;
    private SagaScheduler scheduler;

    @BeforeEach
    void setUp() {
        sagaRepository = mock(SagaInstanceRepository.class);
        orchestrator = mock(SagaOrchestrator.class);
        properties = new SagaProperties();
        properties.setMaxRetries(3);
        properties.setSchedulerRate(Duration.ofSeconds(30));
        properties.getCleanup().setRetention(Duration.ofDays(30));

        scheduler = new SagaScheduler(sagaRepository, properties, orchestrator);
    }

    @Test
    void detectStuckSagas_stuckRunning_marksCompensatingAndTriggersResume() {
        SagaInstance stuckSaga = SagaInstance.builder()
                .id(UUID.randomUUID())
                .sagaType("TestSaga")
                .status(SagaStatus.RUNNING)
                .retryCount(0)
                .updatedAt(Instant.now())
                .build();

        when(sagaRepository.findStuckSagas(eq(SagaStatus.RUNNING), any(Instant.class), eq(3)))
                .thenReturn(List.of(stuckSaga));
        when(sagaRepository.findStuckSagas(eq(SagaStatus.STARTED), any(Instant.class), eq(3)))
                .thenReturn(List.of());

        scheduler.detectStuckSagas();

        // Verify saga marked as COMPENSATING
        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(captor.getValue().getRetryCount()).isEqualTo(1);

        // Verify orchestrator.resumeById was called
        verify(orchestrator).resumeById(stuckSaga.getId());
    }

    @Test
    void detectStuckSagas_stuckStarted_marksFailedWithoutResume() {
        SagaInstance stuckSaga = SagaInstance.builder()
                .id(UUID.randomUUID())
                .sagaType("TestSaga")
                .status(SagaStatus.STARTED)
                .retryCount(1)
                .updatedAt(Instant.now())
                .build();

        when(sagaRepository.findStuckSagas(eq(SagaStatus.RUNNING), any(Instant.class), eq(3)))
                .thenReturn(List.of());
        when(sagaRepository.findStuckSagas(eq(SagaStatus.STARTED), any(Instant.class), eq(3)))
                .thenReturn(List.of(stuckSaga));

        scheduler.detectStuckSagas();

        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(captor.getValue().getRetryCount()).isEqualTo(2);

        // STARTED sagas are not resumed — no steps have run
        verify(orchestrator, never()).resumeById(any());
    }

    @Test
    void detectStuckSagas_noStuckSagas_doesNothing() {
        when(sagaRepository.findStuckSagas(eq(SagaStatus.RUNNING), any(Instant.class), eq(3)))
                .thenReturn(List.of());
        when(sagaRepository.findStuckSagas(eq(SagaStatus.STARTED), any(Instant.class), eq(3)))
                .thenReturn(List.of());

        scheduler.detectStuckSagas();

        verify(sagaRepository, never()).save(any());
        verify(orchestrator, never()).resumeById(any());
    }

    @Test
    void detectStuckSagas_resumeFails_doesNotPropagate() {
        SagaInstance stuckSaga = SagaInstance.builder()
                .id(UUID.randomUUID())
                .sagaType("TestSaga")
                .status(SagaStatus.RUNNING)
                .retryCount(0)
                .updatedAt(Instant.now())
                .build();

        when(sagaRepository.findStuckSagas(eq(SagaStatus.RUNNING), any(Instant.class), eq(3)))
                .thenReturn(List.of(stuckSaga));
        when(sagaRepository.findStuckSagas(eq(SagaStatus.STARTED), any(Instant.class), eq(3)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("No definition registered"))
                .when(orchestrator).resumeById(stuckSaga.getId());

        // Should not throw — exceptions from resumeById are caught
        scheduler.detectStuckSagas();

        verify(orchestrator).resumeById(stuckSaga.getId());
    }

    @Test
    void cleanup_deletesTerminalSagasOlderThanRetention() {
        when(sagaRepository.deleteByStatusInAndUpdatedAtBefore(any(), any(Instant.class)))
                .thenReturn(5);

        scheduler.cleanup();

        ArgumentCaptor<List<SagaStatus>> statusCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(sagaRepository).deleteByStatusInAndUpdatedAtBefore(statusCaptor.capture(), cutoffCaptor.capture());

        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(SagaStatus.COMPLETED, SagaStatus.COMPENSATED, SagaStatus.FAILED);
        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now());
    }

    @Test
    void cleanup_nothingToDelete_doesNotLog() {
        when(sagaRepository.deleteByStatusInAndUpdatedAtBefore(any(), any(Instant.class)))
                .thenReturn(0);

        scheduler.cleanup();

        verify(sagaRepository).deleteByStatusInAndUpdatedAtBefore(any(), any(Instant.class));
    }
}
