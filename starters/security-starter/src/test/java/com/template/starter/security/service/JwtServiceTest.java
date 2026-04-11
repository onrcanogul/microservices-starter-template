package com.template.starter.security.service;

import com.template.starter.security.model.AuthenticatedUser;
import com.template.starter.security.property.SecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    // 64-char secret
    private static final String SECRET = "a]5Dk{P9qR#mL2nX7vJ!wF0zT4bY8cU3eH6gA1iK&oS*dN=fQ+rMxW@pC(jEyV$Z";
    private static final String ISSUER = "acme-enterprise";

    private SecretKey signingKey;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(SECRET, 3600000, ISSUER, null, null);
        jwtService = new JwtService(properties);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = buildToken(UUID.randomUUID(), "test@example.com", List.of("ADMIN"), List.of("order:read"));
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_wrongSignature_returnsFalse() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "WRONG_KEY_THAT_IS_AT_LEAST_64_CHARS_LONG_FOR_HMAC_SHA_256_ALGO!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(jwtService.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void parseToken_withRolesAndPermissions_returnsComplete() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "admin@example.com", List.of("ADMIN", "USER"), List.of("order:read", "order:write"));

        AuthenticatedUser user = jwtService.parseToken(token);

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("admin@example.com");
        assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(user.permissions()).containsExactlyInAnyOrder("order:read", "order:write");
    }

    @Test
    void parseToken_withoutPermissionsClaim_returnsEmptyPermissions() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("email", "user@example.com")
                .claim("roles", List.of("USER"))
                // No 'permissions' claim
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        AuthenticatedUser user = jwtService.parseToken(token);

        assertThat(user.roles()).containsExactly("USER");
        assertThat(user.permissions()).isEmpty();
    }

    @Test
    void parseToken_withoutRolesClaim_returnsEmptyRoles() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("email", "user@example.com")
                .claim("permissions", List.of("order:read"))
                // No 'roles' claim
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        AuthenticatedUser user = jwtService.parseToken(token);

        assertThat(user.roles()).isEmpty();
        assertThat(user.permissions()).containsExactly("order:read");
    }

    private String buildToken(UUID userId, String email, List<String> roles, List<String> permissions) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("email", email)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();
    }
}
