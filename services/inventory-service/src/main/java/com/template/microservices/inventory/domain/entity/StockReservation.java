package com.template.microservices.inventory.domain.entity;

import com.template.core.audit.IInsertAuditing;
import com.template.core.audit.ISoftDelete;
import com.template.core.audit.IUpdateAuditing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;

/**
 * One stock reservation per order. Drives idempotency on the inventory side: a second
 * {@code stock.reservation.requested} for an {@code orderId} that already has a row is a no-op,
 * and a {@code stock.release.requested} only releases a row still in {@code RESERVED}.
 * Mirrors the {@link com.template.microservices.inventory.domain.entity.Stock} audit/annotation style
 * (Order entity convention): {@code @Getter/@Setter}, {@code @Audited}, audit interfaces — never {@code @Data}.
 */
@Entity
@Getter @Setter
@Table(name = "stock_reservation")
@Audited
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
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    @Column(name = "is_deleted")
    private boolean deleted;
    private String deletedBy;
    private Instant deletedAt;

}
