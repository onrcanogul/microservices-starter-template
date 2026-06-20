package com.template.starter.outbox.entity;

import com.template.messaging.event.version.EventVersionUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "IX_IS_PUBLISHED", columnList = "IS_PUBLISHED"),
        @Index(name = "IX_OUTBOX_DEAD", columnList = "DEAD")
})
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Outbox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID")
    private UUID id;
    @Column(name = "AGGREGATETYPE")
    private String aggregateType;
    @Column(name = "AGGREGATEID")
    private String aggregateId;

    @Column(name = "TYPE")
    private String type;
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "PAYLOAD", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "DESTINATION")
    private String destination;
    @Column(name = "CORRELATION_ID")
    private String correlationId;
    @Column(name = "IS_PUBLISHED")
    private boolean published = false;
    @Builder.Default
    @Column(name = "VERSION")
    private int version = EventVersionUtil.DEFAULT_VERSION;
    @Column(name = "CREATED_AT")
    private Instant createdAt = Instant.now();

    // ---- publish-layer retry / dead-letter (poison-message handling) ----
    /** Number of publish attempts so far. */
    @Builder.Default
    @Column(name = "ATTEMPTS")
    private int attempts = 0;
    /** Last publish error (truncated), for visibility. */
    @Column(name = "LAST_ERROR", columnDefinition = "TEXT")
    private String lastError;
    /** Terminal: exhausted publish retries. Not retried; awaits replay. */
    @Builder.Default
    @Column(name = "DEAD")
    private boolean dead = false;
    /** Earliest time the row is eligible for the next publish attempt (backoff). Null = due now. */
    @Column(name = "NEXT_ATTEMPT_AT")
    private Instant nextAttemptAt;
}
