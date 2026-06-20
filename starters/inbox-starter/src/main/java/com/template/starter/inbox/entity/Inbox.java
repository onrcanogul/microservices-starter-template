package com.template.starter.inbox.entity;

import com.template.messaging.event.version.EventVersionUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox", indexes = {
        @Index(name = "IX_IS_PROCESSED", columnList = "IS_PROCESSED"),
        @Index(name = "IX_INBOX_DEAD", columnList = "DEAD")
})
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class Inbox {
    @Id
    @Column(name = "IDEMPOTENT_TOKEN", unique = true)
    private UUID idempotentToken;
    @Column(name = "PAYLOAD")
    private String payload;
    @Column(name = "TYPE")
    private String type;
    @Column(name = "IS_PROCESSED")
    private boolean processed = false;
    @Builder.Default
    @Column(name = "VERSION")
    private int version = EventVersionUtil.DEFAULT_VERSION;
    @Column(name = "RECEIVED_AT")
    private Instant receivedAt;

    // ---- processing-layer retry / dead-letter (poison-message handling) ----
    /** Number of processing attempts so far. */
    @Builder.Default
    @Column(name = "ATTEMPTS")
    private int attempts = 0;
    /** Last processing error (truncated), for visibility. */
    @Column(name = "LAST_ERROR", columnDefinition = "TEXT")
    private String lastError;
    /** Terminal: exhausted retries or hit a non-retryable error. Not retried; awaits replay. */
    @Builder.Default
    @Column(name = "DEAD")
    private boolean dead = false;
    /** Earliest time the row is eligible for the next attempt (backoff). Null = due now. */
    @Column(name = "NEXT_ATTEMPT_AT")
    private Instant nextAttemptAt;
}
