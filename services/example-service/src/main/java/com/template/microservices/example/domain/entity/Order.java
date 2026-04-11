package com.template.microservices.example.domain.entity;

import com.template.core.audit.IInsertAuditing;
import com.template.core.audit.ISoftDelete;
import com.template.core.audit.IUpdateAuditing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;

@Entity
@Getter @Setter
@Table(name = "orders")
@Audited
public class Order implements IInsertAuditing, IUpdateAuditing, ISoftDelete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private Integer amount;
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
