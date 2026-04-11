package com.template.starter.audit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import com.template.starter.audit.listener.CustomRevisionListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Custom Envers revision entity that captures user context from MDC.
 *
 * <p>Each database transaction that modifies an {@code @Audited} entity
 * creates one revision record. This entity stores who made the change,
 * their email, and the correlation ID for distributed tracing.</p>
 */
@Entity
@Getter
@Setter
@Table(name = "revinfo")
@RevisionEntity(CustomRevisionListener.class)
public class CustomRevisionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private Long rev;

    @RevisionTimestamp
    @Column(name = "revtstmp", nullable = false)
    private long revtstmp;

    /**
     * User ID who made the change (from MDC, set by MdcFilter).
     */
    @Column(name = "user_id")
    private String userId;

    /**
     * User email who made the change (from MDC, set by MdcFilter).
     */
    @Column(name = "user_email")
    private String userEmail;

    /**
     * Correlation ID for distributed tracing (from MDC, set by MdcFilter).
     */
    @Column(name = "correlation_id")
    private String correlationId;

    /**
     * Returns the revision timestamp as an {@link Instant}.
     */
    public Instant getRevisionInstant() {
        return Instant.ofEpochMilli(revtstmp);
    }
}
