package com.template.observer.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "acme.obs")
public class ObservabilityProperties {

    private boolean addTraceResponseHeader = true;

    private int maxUniqueHttpUris = 200;

    private Map<String, String> commonTags = Map.of();

    private Tracing tracing = new Tracing();

    public static class Tracing {
        private boolean enabled = true;
        /** (0.0–1.0) */
        private double probability = 0.10;
        private boolean otlpEnabled = false;
        private String otlpEndpoint = "http://otel-collector:4317";

        // getters/setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getProbability() { return probability; }
        public void setProbability(double probability) { this.probability = probability; }
        public boolean isOtlpEnabled() { return otlpEnabled; }
        public void setOtlpEnabled(boolean otlpEnabled) { this.otlpEnabled = otlpEnabled; }
        public String getOtlpEndpoint() { return otlpEndpoint; }
        public void setOtlpEndpoint(String otlpEndpoint) { this.otlpEndpoint = otlpEndpoint; }
    }

    // getters/setters
    public boolean isAddTraceResponseHeader() { return addTraceResponseHeader; }
    public void setAddTraceResponseHeader(boolean v) { this.addTraceResponseHeader = v; }
    public int getMaxUniqueHttpUris() { return maxUniqueHttpUris; }
    public void setMaxUniqueHttpUris(int v) { this.maxUniqueHttpUris = v; }
    public Map<String, String> getCommonTags() { return commonTags; }
    public void setCommonTags(Map<String, String> commonTags) { this.commonTags = commonTags; }
    public Tracing getTracing() { return tracing; }
    public void setTracing(Tracing tracing) { this.tracing = tracing; }
}
