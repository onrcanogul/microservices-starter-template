package com.template.starter.saga.entity;

import com.template.messaging.saga.StepStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_step_execution", indexes = {
        @Index(name = "IX_STEP_SAGA_ID", columnList = "SAGA_INSTANCE_ID")
})
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class SagaStepExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SAGA_INSTANCE_ID", nullable = false)
    private SagaInstance sagaInstance;

    @Column(name = "STEP_NAME", nullable = false)
    private String stepName;

    @Column(name = "STEP_ORDER", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private StepStatus status;

    /** JSON output from step execution (optional). */
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "OUTPUT", columnDefinition = "TEXT")
    private String output;

    /** Error reason when status is FAILED. */
    @Column(name = "FAILURE_REASON")
    private String failureReason;

    @Column(name = "EXECUTED_AT")
    private Instant executedAt;

    @Column(name = "COMPENSATED_AT")
    private Instant compensatedAt;
}
