package com.template.starter.audit.service;

import com.template.starter.audit.entity.CustomRevisionEntity;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditquerydb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.org.hibernate.envers.audit_table_suffix=_aud",
        "spring.jpa.properties.org.hibernate.envers.revision_table_name=revinfo",
        "spring.jpa.properties.org.hibernate.envers.store_data_at_delete=true"
})
class AuditQueryServiceTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {AuditQueryServiceTest.class})
    static class TestConfig {
    }

    @Entity
    @Table(name = "test_product")
    @Audited
    static class TestProduct {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private int price;

        TestProduct() {}

        TestProduct(String name, int price) {
            this.name = name;
            this.price = price;
        }

        Long getId() { return id; }
        String getName() { return name; }
        void setName(String name) { this.name = name; }
        int getPrice() { return price; }
        void setPrice(int price) { this.price = price; }
    }

    @Autowired
    private AuditQueryService auditQueryService;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    private Long createProduct(String name, int price) {
        return transactionTemplate.execute(status -> {
            TestProduct product = new TestProduct(name, price);
            em.persist(product);
            em.flush();
            return product.getId();
        });
    }

    private void updateProduct(Long id, String name, int price) {
        transactionTemplate.execute(status -> {
            TestProduct product = em.find(TestProduct.class, id);
            product.setName(name);
            product.setPrice(price);
            em.merge(product);
            em.flush();
            return null;
        });
    }

    @Test
    void getHistory_afterCreate_returnsOneAddRevision() {
        MDC.put("userId", "user-1");
        MDC.put("userEmail", "user1@test.com");

        Long id = createProduct("Widget", 100);

        List<AuditQueryService.AuditRevision<TestProduct>> history =
                auditQueryService.getHistory(TestProduct.class, id);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().type()).isEqualTo(RevisionType.ADD);
        assertThat(history.getFirst().entity().getName()).isEqualTo("Widget");
        assertThat(history.getFirst().revision().getUserId()).isEqualTo("user-1");
        assertThat(history.getFirst().revision().getUserEmail()).isEqualTo("user1@test.com");
        MDC.clear();
    }

    @Test
    void getHistory_afterCreateAndUpdate_returnsTwoRevisions() {
        Long id = createProduct("Widget", 100);
        updateProduct(id, "Widget", 200);

        List<AuditQueryService.AuditRevision<TestProduct>> history =
                auditQueryService.getHistory(TestProduct.class, id);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).type()).isEqualTo(RevisionType.ADD);
        assertThat(history.get(0).entity().getPrice()).isEqualTo(100);
        assertThat(history.get(1).type()).isEqualTo(RevisionType.MOD);
        assertThat(history.get(1).entity().getPrice()).isEqualTo(200);
    }

    @Test
    void getAtRevision_returnsCorrectSnapshot() {
        Long id = createProduct("Widget", 100);
        updateProduct(id, "Updated Widget", 100);

        List<Number> revisions = auditQueryService.getRevisions(TestProduct.class, id);
        assertThat(revisions).hasSize(2);

        TestProduct atFirstRevision = auditQueryService.getAtRevision(
                TestProduct.class, id, revisions.get(0).longValue());
        assertThat(atFirstRevision.getName()).isEqualTo("Widget");

        TestProduct atSecondRevision = auditQueryService.getAtRevision(
                TestProduct.class, id, revisions.get(1).longValue());
        assertThat(atSecondRevision.getName()).isEqualTo("Updated Widget");
    }

    @Test
    void getRevisions_returnsOrderedList() {
        Long id = createProduct("Widget", 100);
        updateProduct(id, "Widget", 200);
        updateProduct(id, "Widget", 300);

        List<Number> revisions = auditQueryService.getRevisions(TestProduct.class, id);
        assertThat(revisions).hasSize(3);
        assertThat(revisions.get(0).longValue()).isLessThan(revisions.get(1).longValue());
        assertThat(revisions.get(1).longValue()).isLessThan(revisions.get(2).longValue());
    }

    @Test
    void getRevisionEntity_returnsMetadata() {
        MDC.put("userId", "audit-user");
        MDC.put("correlationId", "corr-123");

        Long id = createProduct("Widget", 100);

        List<Number> revisions = auditQueryService.getRevisions(TestProduct.class, id);
        CustomRevisionEntity revEntity = auditQueryService.getRevisionEntity(revisions.getFirst().longValue());

        assertThat(revEntity).isNotNull();
        assertThat(revEntity.getUserId()).isEqualTo("audit-user");
        assertThat(revEntity.getCorrelationId()).isEqualTo("corr-123");
        assertThat(revEntity.getRevisionInstant()).isBefore(Instant.now());
        MDC.clear();
    }

    @Test
    void getAtPointInTime_noRevisionsBeforeDate_returnsNull() {
        Instant beforeAny = Instant.parse("2000-01-01T00:00:00Z");
        TestProduct result = auditQueryService.getAtPointInTime(TestProduct.class, 999L, beforeAny);
        assertThat(result).isNull();
    }

    @Test
    void getAtPointInTime_afterCreate_returnsEntity() {
        Long id = createProduct("Widget", 100);

        Instant afterCreate = Instant.now().plusSeconds(10);
        TestProduct result = auditQueryService.getAtPointInTime(TestProduct.class, id, afterCreate);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Widget");
    }
}
