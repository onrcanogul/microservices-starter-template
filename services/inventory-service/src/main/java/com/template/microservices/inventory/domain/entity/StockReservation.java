package com.template.microservices.inventory.domain.entity;

import com.template.core.audit.IInsertAuditing;
import com.template.core.audit.ISoftDelete;
import com.template.core.audit.IUpdateAuditing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * One stock reservation per order. Drives idempotency on the inventory side: a second
 * {@code stock.reservation.requested} for an {@code orderId} that already has a row is a no-op, and a
 * {@code stock.release.requested} only releases a row still in {@code RESERVED}. Same audit/annotation
 * style as {@link Stock} (Order convention) with Spring Data JPA auditing for the NOT NULL columns.
 */
@Entity
@Getter @Setter
@Table(name = "stock_reservation")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class StockReservation implements IInsertAuditing, IUpdateAuditing, ISoftDelete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long orderId;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private Integer amount;
    @Column(nullable = false)
    private String status = "RESERVED";
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;
    @LastModifiedDate
    private Instant updatedAt;
    @LastModifiedBy
    private String updatedBy;
    @Column(name = "is_deleted")
    private boolean deleted;
    private String deletedBy;
    private Instant deletedAt;

}
