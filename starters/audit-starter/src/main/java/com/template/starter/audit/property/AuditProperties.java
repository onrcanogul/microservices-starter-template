package com.template.starter.audit.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "acme.audit")
public class AuditProperties {

    private boolean enabled = true;

    private boolean storeDataAtDelete = true;

    private boolean modifiedFlags = false;

    private String auditTableSuffix = "_aud";

    private String revisionTableName = "revinfo";
}
