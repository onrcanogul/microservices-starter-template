package com.template.starter.security.service;

import com.template.starter.security.model.AuthenticatedUser;
import com.template.starter.security.property.SecurityProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey signingKey;
    private final String issuer;

    public JwtService(SecurityProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
            properties.secret().getBytes(StandardCharsets.UTF_8)
        );
        this.issuer = properties.issuer();
    }

    public boolean validateToken(String token) {
        try {
            parseClaimsFromToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = parseClaimsFromToken(token);

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Token subject (sub) must not be blank");
        }

        UUID userId = UUID.fromString(subject);
        String email = claims.get("email", String.class);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        Set<String> roleSet = (roles != null) ? Set.copyOf(roles) : Set.of();

        return new AuthenticatedUser(userId, email, roleSet);
    }

    private Claims parseClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
