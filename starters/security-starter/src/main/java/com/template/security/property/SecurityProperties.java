package com.template.security.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "acme.security")
public class SecurityProperties {

    private boolean enabled = true;

    private List<String> permitAll = List.of("/actuator/health", "/actuator/info");

    private boolean csrfEnabled = false;

    private Cors cors = new Cors();

    private Jwt jwt = new Jwt();

    private Method method = new Method();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getPermitAll() { return permitAll; }
    public void setPermitAll(List<String> permitAll) { this.permitAll = permitAll; }

    public boolean isCsrfEnabled() { return csrfEnabled; }
    public void setCsrfEnabled(boolean csrfEnabled) { this.csrfEnabled = csrfEnabled; }

    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public static class Cors {
        private boolean enabled = true;
        private List<String> allowedOrigins = List.of("*");
        private List<String> allowedMethods = List.of("GET","POST","PUT","DELETE","OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }
    }

    public static class Jwt {
        private boolean enabled = true;
        private String resourceId = "";
        private String authorityPrefix = "ROLE_";
        private boolean addRealmRoles = true;
        private boolean addResourceRoles = true;
        private boolean addScopeAuthorities = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getAuthorityPrefix() { return authorityPrefix; }
        public void setAuthorityPrefix(String authorityPrefix) { this.authorityPrefix = authorityPrefix; }
        public boolean isAddRealmRoles() { return addRealmRoles; }
        public void setAddRealmRoles(boolean addRealmRoles) { this.addRealmRoles = addRealmRoles; }
        public boolean isAddResourceRoles() { return addResourceRoles; }
        public void setAddResourceRoles(boolean addResourceRoles) { this.addResourceRoles = addResourceRoles; }
        public boolean isAddScopeAuthorities() { return addScopeAuthorities; }
        public void setAddScopeAuthorities(boolean addScopeAuthorities) { this.addScopeAuthorities = addScopeAuthorities; }
    }

    public static class Method {
        private boolean enabled = false; // @PreAuthorize
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}

