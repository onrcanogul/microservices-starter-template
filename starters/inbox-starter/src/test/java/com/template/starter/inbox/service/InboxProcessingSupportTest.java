package com.template.starter.inbox.service;

import com.template.core.error.StandardErrorCodes;
import com.template.core.exception.BusinessException;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.property.InboxProperties;
import com.template.starter.inbox.repository.InboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the inbox processing-layer poison-message lifecycle (retry / dead-letter), using a
 * mock repository and an inline transaction template (no DB). The real-DB path is covered by
 * {@code InboxPoisonMessageIntegrationTest} (Testcontainers).
 */
class InboxProcessingSupportTest {

    private InboxRepository repository;
    private InboxProperties properties;
    private InboxProcessingSupport support;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(InboxRepository.class);
        properties = new InboxProperties();
        properties.getRetry().setMaxAttempts(3);

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        when(meterProvider.getIfAvailable()).thenReturn(null);

        support = new InboxProcessingSupport(repository, new TransactionTemplate(txm), properties, meterProvider);
    }

    private Inbox eligibleRow() {
        Inbox inbox = Inbox.builder().idempotentToken(UUID.randomUUID())
                .type("T").payload("{}").processed(false).version(1).build();
        when(repository.findEligible(any(), any())).thenReturn(List.of(inbox));
        when(repository.findById(any())).thenReturn(Optional.of(inbox));
        return inbox;
    }

    @Test
    void success_marksProcessedAndClearsError() {
        Inbox inbox = eligibleRow();
        inbox.setLastError("previous");

        support.process(noop());

        assertThat(inbox.isProcessed()).isTrue();
        assertThat(inbox.isDead()).isFalse();
        assertThat(inbox.getLastError()).isNull();
    }

    @Test
    void transientError_incrementsAttemptsAndSchedulesRetry() {
        Inbox inbox = eligibleRow();

        support.process(throwing(new RuntimeException("transient")));

        assertThat(inbox.getAttempts()).isEqualTo(1);
        assertThat(inbox.isDead()).isFalse();
        assertThat(inbox.isProcessed()).isFalse();
        assertThat(inbox.getNextAttemptAt()).isNotNull();
        assertThat(inbox.getLastError()).contains("transient");
    }

    @Test
    void businessException_isNonRetryable_deadImmediately() {
        Inbox inbox = eligibleRow();

        support.process(throwing(BusinessException.of(StandardErrorCodes.VALIDATION_FAILED, "bad event")));

        assertThat(inbox.isDead()).isTrue();
        assertThat(inbox.getAttempts()).isEqualTo(1);
        assertThat(inbox.getNextAttemptAt()).isNull();
        assertThat(inbox.getLastError()).contains("bad event");
    }

    @Test
    void exhaustingMaxAttempts_marksDead() {
        Inbox inbox = eligibleRow();
        inbox.setAttempts(properties.getRetry().getMaxAttempts() - 1); // next failure crosses the threshold

        support.process(throwing(new RuntimeException("still failing")));

        assertThat(inbox.getAttempts()).isEqualTo(properties.getRetry().getMaxAttempts());
        assertThat(inbox.isDead()).isTrue();
        assertThat(inbox.getNextAttemptAt()).isNull();
    }

    private static Consumer<Inbox> noop() {
        return inbox -> { };
    }

    private static Consumer<Inbox> throwing(RuntimeException e) {
        return inbox -> { throw e; };
    }
}
