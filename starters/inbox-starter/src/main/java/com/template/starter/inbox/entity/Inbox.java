package com.template.starter.inbox.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbox", indexes = {
        @Index(name = "IX_IS_PROCESSED", columnList = "IS_PROCESSED")
})
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class Inbox {
    @Id
    @Column(name = "IDEMPOTENT_TOKEN")
    private UUID idempotentToken;
    @Column(name = "PAYLOAD")
    private String payload;
    @Column(name = "TYPE")
    private String type;
    @Column(name = "IS_PROCESSED")
    private boolean processed = false;
    @Column(name = "RECEIVED_AT")
    private LocalDateTime receivedAt;
}
