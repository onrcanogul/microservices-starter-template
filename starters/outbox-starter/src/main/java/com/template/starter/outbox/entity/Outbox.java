package com.template.starter.outbox.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "IX_IS_PUBLISHED", columnList = "IS_PUBLISHED")
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
    @Column(name = "IS_PUBLISHED")
    private boolean published = false;
    @Column(name = "CREATED_AT")
    private Instant createdAt = Instant.now();
}
