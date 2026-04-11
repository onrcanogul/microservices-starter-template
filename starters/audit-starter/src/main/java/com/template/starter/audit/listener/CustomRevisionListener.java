package com.template.starter.audit.listener;

import com.template.starter.audit.entity.CustomRevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.slf4j.MDC;

/**
 * Envers revision listener that populates {@link CustomRevisionEntity}
 * with user context from SLF4J MDC.
 *
 * <p>MDC keys are set by the logging-starter's {@code MdcFilter}:
 * {@code userId}, {@code userEmail}, {@code correlationId}.</p>
 *
 * <p>When no MDC context is available (e.g., batch jobs, Kafka consumers),
 * fields are left null and the revision still records the timestamp.</p>
 */
public class CustomRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        CustomRevisionEntity rev = (CustomRevisionEntity) revisionEntity;
        rev.setUserId(MDC.get("userId"));
        rev.setUserEmail(MDC.get("userEmail"));
        rev.setCorrelationId(MDC.get("correlationId"));
    }
}
