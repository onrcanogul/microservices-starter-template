package com.template.microservices.example.domain.entity;

import com.template.core.audit.IInsertAuditing;
import com.template.core.audit.ISoftDelete;
import com.template.core.audit.IUpdateAuditing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter @Setter
public class Order implements IInsertAuditing, IUpdateAuditing, ISoftDelete {
    @Id
    private Long id;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private Integer amount;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = true)
    private Instant updatedAt;
    @Column(nullable = true)
    private String updatedBy;
    @Column(nullable = true)
    private boolean isDeleted;
    @Column(nullable = true)
    private String deletedBy;
    @Column(nullable = true)
    private Instant deletedAt;
}
