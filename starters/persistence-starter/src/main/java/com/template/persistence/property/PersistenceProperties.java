package com.template.persistence.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme.persistence")
public class PersistenceProperties {

    private boolean openInView = false;       // OSIV kapalı
    private String ddlAuto = "none";          // prod için güvenli default
    private String timeZone = "UTC";          // JDBC timezone

    private int jdbcBatchSize = 50;           // batch insert/update
    private boolean orderInserts = true;
    private boolean orderUpdates = true;
    private boolean showSql = false;          // dev'de serviste açabilirsin

    private Tx tx = new Tx();
    private Jpa jpa = new Jpa();

    public static class Tx {
        private int defaultTimeoutSeconds = 30;
        public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
        public void setDefaultTimeoutSeconds(int v) { this.defaultTimeoutSeconds = v; }
    }
    public static class Jpa {
        private boolean auditingEnabled = true;  // AuditorAware yoksa "system"
        public boolean isAuditingEnabled() { return auditingEnabled; }
        public void setAuditingEnabled(boolean auditingEnabled) { this.auditingEnabled = auditingEnabled; }
    }

    public boolean isOpenInView() { return openInView; }
    public void setOpenInView(boolean openInView) { this.openInView = openInView; }
    public String getDdlAuto() { return ddlAuto; }
    public void setDdlAuto(String ddlAuto) { this.ddlAuto = ddlAuto; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public int getJdbcBatchSize() { return jdbcBatchSize; }
    public void setJdbcBatchSize(int jdbcBatchSize) { this.jdbcBatchSize = jdbcBatchSize; }
    public boolean isOrderInserts() { return orderInserts; }
    public void setOrderInserts(boolean orderInserts) { this.orderInserts = orderInserts; }
    public boolean isOrderUpdates() { return orderUpdates; }
    public void setOrderUpdates(boolean orderUpdates) { this.orderUpdates = orderUpdates; }
    public boolean isShowSql() { return showSql; }
    public void setShowSql(boolean showSql) { this.showSql = showSql; }
    public Tx getTx() { return tx; }
    public void setTx(Tx tx) { this.tx = tx; }
    public Jpa getJpa() { return jpa; }
    public void setJpa(Jpa jpa) { this.jpa = jpa; }
}

