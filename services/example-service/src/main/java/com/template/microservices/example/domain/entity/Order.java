package com.template.microservices.example.domain.entity;

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

@Entity
@Getter @Setter
@Table(name = "orders")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Order implements IInsertAuditing, IUpdateAuditing, ISoftDelete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private Integer amount;
    // Saga state: PENDING -> CONFIRMED | REJECTED (reservation outcome); CANCELLED on compensation.
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(nullable = false)
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
