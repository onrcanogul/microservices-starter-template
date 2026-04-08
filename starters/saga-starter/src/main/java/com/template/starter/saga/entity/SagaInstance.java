package com.template.starter.saga.entity;

import com.template.messaging.saga.SagaStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saga_instance", indexes = {
        @Index(name = "IX_SAGA_STATUS", columnList = "STATUS"),
        @Index(name = "IX_SAGA_CORRELATION", columnList = "CORRELATION_ID")
})
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID")
    private UUID id;

    /** Saga type identifier (e.g., "CreateOrderSaga"). */
    @Column(name = "SAGA_TYPE", nullable = false)
    private String sagaType;

    /** Correlation ID for tracing across services. */
    @Column(name = "CORRELATION_ID", nullable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private SagaStatus status;

    /** JSON-serialized saga context. */
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "PAYLOAD", columnDefinition = "TEXT")
    private String payload;

    /** Index of the current step (0-based). */
    @Column(name = "CURRENT_STEP")
    private int currentStep;

    /** Number of recovery attempts for stuck sagas. */
    @Column(name = "RETRY_COUNT")
    private int retryCount;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    /** Deadline after which the saga is considered stuck. */
    @Column(name = "DEADLINE_AT")
    private Instant deadlineAt;

    @OneToMany(mappedBy = "sagaInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<SagaStepExecution> steps = new ArrayList<>();
}
