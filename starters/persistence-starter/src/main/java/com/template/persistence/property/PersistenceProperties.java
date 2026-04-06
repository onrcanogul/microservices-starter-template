package com.template.persistence.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme.persistence")
public class PersistenceProperties {

    private boolean openInView = false;
    private String ddlAuto = "none";
    private String timeZone = "UTC";

    private int jdbcBatchSize = 50;
    private boolean orderInserts = true;
    private boolean orderUpdates = true;
    private boolean showSql = false;

    private Tx tx = new Tx();
    private Jpa jpa = new Jpa();
    private Hikari hikari = new Hikari();

    public static class Tx {
        private int defaultTimeoutSeconds = 30;
        public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
        public void setDefaultTimeoutSeconds(int v) { this.defaultTimeoutSeconds = v; }
    }
    public static class Jpa {
        private boolean auditingEnabled = true;
        public boolean isAuditingEnabled() { return auditingEnabled; }
        public void setAuditingEnabled(boolean auditingEnabled) { this.auditingEnabled = auditingEnabled; }
    }

    /**
     * HikariCP connection pool tuning.
     * Defaults are optimised for a typical Spring Boot microservice
     * with moderate traffic. Override per-service via config-server.
     */
    public static class Hikari {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeoutMs = 30_000;
        private long idleTimeoutMs = 600_000;
        private long maxLifetimeMs = 1_800_000;
        private long leakDetectionThresholdMs = 60_000;
        private long validationTimeoutMs = 5_000;
        private long keepaliveTimeMs = 300_000;

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int v) { this.maximumPoolSize = v; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int v) { this.minimumIdle = v; }
        public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public void setConnectionTimeoutMs(long v) { this.connectionTimeoutMs = v; }
        public long getIdleTimeoutMs() { return idleTimeoutMs; }
        public void setIdleTimeoutMs(long v) { this.idleTimeoutMs = v; }
        public long getMaxLifetimeMs() { return maxLifetimeMs; }
        public void setMaxLifetimeMs(long v) { this.maxLifetimeMs = v; }
        public long getLeakDetectionThresholdMs() { return leakDetectionThresholdMs; }
        public void setLeakDetectionThresholdMs(long v) { this.leakDetectionThresholdMs = v; }
        public long getValidationTimeoutMs() { return validationTimeoutMs; }
        public void setValidationTimeoutMs(long v) { this.validationTimeoutMs = v; }
        public long getKeepaliveTimeMs() { return keepaliveTimeMs; }
        public void setKeepaliveTimeMs(long v) { this.keepaliveTimeMs = v; }
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
    public Hikari getHikari() { return hikari; }
    public void setHikari(Hikari hikari) { this.hikari = hikari; }
}

