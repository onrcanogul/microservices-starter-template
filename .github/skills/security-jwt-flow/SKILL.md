---
name: security-jwt-flow
description: "Use when configuring JWT security, adding public paths, modifying token validation, debugging authentication errors, or understanding the two-layer security architecture (gateway reactive filter vs service servlet filter). Covers SecurityProperties validation, JwtService, SecurityFilterChain, AuthenticatedUser, and the X-User-Id header propagation pattern."
---

# Security & JWT Flow

This project implements a two-layer JWT security architecture. The **API gateway** validates tokens and propagates user identity as headers. Each **service** independently validates tokens via the `security-starter`. Understanding this dual-validation model prevents common misconfigurations.

## Two-Layer Architecture

```
Client → [JWT in Authorization header]
    → API Gateway (reactive JwtAuthFilter)
        → Validates JWT, extracts X-User-Id + X-User-Email headers
    → Service (servlet JwtAuthenticationFilter via security-starter)
        → Validates JWT again, sets SecurityContext
    → Controller
        → Access AuthenticatedUser via SecurityContext principal
```

Both layers validate the JWT independently. If the gateway is bypassed (e.g., internal service-to-service calls), the service-level filter still protects endpoints.

## SecurityProperties (Record with Validation)

```java
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
                "JWT secret must not be blank. Set the 'acme.security.jwt.secret' property.");
        }
        if (secret.length() < 64) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 64 characters (256-bit). Current length: " + secret.length());
        }
    }

    public long expirationMs() {
        return expirationMs > 0 ? expirationMs : 3600000; // 1 hour default
    }

    public String issuer() {
        return issuer != null ? issuer : "acme-enterprise";
    }
}
```

The compact constructor validation is strict — the app **will not start** with a weak or missing secret. This catches misconfigurations early.

Configuration:
```yaml
acme:
  security:
    jwt:
      secret: ${JWT_SECRET}          # env var, ≥64 chars, NEVER hardcode
      expiration-ms: 3600000         # 1 hour (optional, has default)
      issuer: acme-enterprise        # JWT issuer claim (optional)
      public-paths:                  # additional paths that skip auth
        - /api/public/**
        - /api/health
```

## JwtService

Handles token validation and parsing:

```java
// Validation — called by the filter
boolean isValid = jwtService.validateToken(token);

// Parsing — extracts user details
AuthenticatedUser user = jwtService.parseToken(token);
// user.id()     → UUID (from JWT subject)
// user.email()   → String (from 'email' claim)
// user.roles()   → Set<String> (from 'roles' claim)
```

Validation checks:
- JWT signature (HMAC-SHA with configured secret key)
- Issuer matches configured issuer
- Expiration not passed
- Token is well-formed

On failure, logs a warning with the specific failure reason (expired, malformed, invalid signature, etc.) and returns `false`.

## JwtAuthenticationFilter (Service-Level)

The servlet filter in `security-starter`:

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;  // no token = anonymous, Spring Security handles 401
        }

        String token = authHeader.substring(7);
        if (jwtService.validateToken(token)) {
            AuthenticatedUser user = jwtService.parseToken(token);
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    user, null,
                    user.roles().stream().map(SimpleGrantedAuthority::new).toList()
                )
            );
        }
        filterChain.doFilter(request, response);
    }
}
```

The `AuthenticatedUser` record becomes the `principal` — access it in controllers:

```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserDto>> getProfile(Authentication auth) {
    AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
    // user.id(), user.email(), user.roles()
}
```

## SecurityFilterChain

Auto-configured by `security-starter`:

```java
@Bean
SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, ...) {
    return http
        .csrf(csrf -> csrf.disable())                    // stateless API
        .authorizeHttpRequests(auth -> {
            auth.requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/actuator/health",
                "/actuator/info"
            ).permitAll();
            if (properties.publicPaths() != null) {
                auth.requestMatchers(properties.publicPaths()).permitAll();
            }
            auth.anyRequest().authenticated();
        })
        .sessionManagement(sess ->
            sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

### Default Public Paths (always permitted)
- `/v3/api-docs/**` — OpenAPI docs
- `/swagger-ui/**` — Swagger UI
- `/swagger-ui.html`
- `/actuator/health` — health probe
- `/actuator/info`

### Adding Service-Specific Public Paths

Configure via properties:
```yaml
acme:
  security:
    jwt:
      public-paths:
        - /api/public/**
        - /webhook/**
```

## AuthenticatedUser Record

```java
public record AuthenticatedUser(
    UUID id,
    String email,
    Set<String> roles
) {}
```

JWT claims mapping:
- `sub` (subject) → `id` (parsed as UUID)
- `email` → `email`
- `roles` → `roles` (List in JWT, converted to Set)

## K8s Secret Management

```yaml
# infra/k8s/jwt-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: default
type: Opaque
stringData:
  JWT_SECRET: ""  # set via kubectl, do not commit real values
```

Services reference via `envFrom: - secretRef: name: jwt-secret`.

## Gotchas

- The secret must be the **same** across gateway and all services. Use a shared K8s secret or env var.
- The 64-character minimum is enforced at startup **in services using `security-starter`** — shorter secrets crash those services. The API gateway reads the secret via `@Value` directly and does not apply this check.
- `@ConditionalOnMissingBean` on the `SecurityFilterChain` means you can override the entire chain in your service if needed.
- Error handling: `AuthenticationEntryPoint` returns 401, `AccessDeniedHandler` returns 403 — both wired in the auto-configured `SecurityFilterChain`.
- Roles from JWT are mapped directly to `SimpleGrantedAuthority` **without a `ROLE_` prefix**. Use `@PreAuthorize("hasAuthority('ADMIN')")`, not `hasRole('ADMIN')` (which expects the `ROLE_` prefix).
- When testing, the JWT secret in `application-test.yml` must be ≥64 characters.
- The gateway does **not** validate the JWT issuer (`requireIssuer`), but the service-level `JwtService` does. A token with a wrong issuer passes the gateway but fails at the service.
