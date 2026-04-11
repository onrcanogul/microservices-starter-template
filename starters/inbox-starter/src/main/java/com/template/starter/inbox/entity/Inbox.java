package com.template.starter.inbox.entity;

import com.template.messaging.event.version.EventVersionUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox", indexes = {
        @Index(name = "IX_IS_PROCESSED", columnList = "IS_PROCESSED")
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
}
