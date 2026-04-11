package com.template.starter.audit.listener;

import com.template.starter.audit.entity.CustomRevisionEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRevisionListenerTest {

    private final CustomRevisionListener listener = new CustomRevisionListener();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void newRevision_populatesFromMdc() {
        MDC.put("userId", "user-42");
        MDC.put("userEmail", "user@test.com");
        MDC.put("correlationId", "corr-abc");

        CustomRevisionEntity rev = new CustomRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getUserId()).isEqualTo("user-42");
        assertThat(rev.getUserEmail()).isEqualTo("user@test.com");
        assertThat(rev.getCorrelationId()).isEqualTo("corr-abc");
    }

    @Test
    void newRevision_handlesEmptyMdc() {
        CustomRevisionEntity rev = new CustomRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getUserId()).isNull();
        assertThat(rev.getUserEmail()).isNull();
        assertThat(rev.getCorrelationId()).isNull();
    }

    @Test
    void newRevision_handlesPartialMdc() {
        MDC.put("userId", "user-99");

        CustomRevisionEntity rev = new CustomRevisionEntity();
        listener.newRevision(rev);

        assertThat(rev.getUserId()).isEqualTo("user-99");
        assertThat(rev.getUserEmail()).isNull();
        assertThat(rev.getCorrelationId()).isNull();
    }
}
