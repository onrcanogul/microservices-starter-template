package com.template.starter.security.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme.security.jwt")
public record SecurityProperties(
    String secret,
    long expirationMs,
    String issuer,
    String[] publicPaths
) {
    public SecurityProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                "JWT secret must not be blank. Set the 'acme.security.jwt.secret' property."
            );
        }
        if (secret.length() < 64) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 64 characters (256-bit). Current length: " + secret.length()
            );
        }
    }

    // Default values
    public long expirationMs() {
        return expirationMs > 0 ? expirationMs : 3600000; // 1 hour
    }

    public String issuer() {
        return issuer != null ? issuer : "acme-enterprise";
    }
}
