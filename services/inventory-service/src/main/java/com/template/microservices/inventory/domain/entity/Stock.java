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
 * Stock level for one sku: {@code available} can be reserved, {@code reserved} is held against orders.
 * Follows the Order entity convention — {@code @Getter/@Setter} (never {@code @Data}), {@code @Audited}
 * (Hibernate Envers history), audit interfaces. Unlike Order, this entity is actually persisted at
 * runtime, so it wires the template's Spring Data JPA auditing ({@code @CreatedBy}/{@code @CreatedDate},
 * populated by the persistence-starter's {@code AuditorAware}) to fill the NOT NULL audit columns.
 */
@Entity
@Getter @Setter
@Table(name = "stock")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Stock implements IInsertAuditing, IUpdateAuditing, ISoftDelete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String sku;
    @Column(nullable = false)
    private Integer available;
    @Column(nullable = false)
    private Integer reserved;
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
