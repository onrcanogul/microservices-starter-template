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
    String[] publicPaths,
    String[] roleHierarchy
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
      role-hierarchy:                # optional, Spring Security role hierarchy
        - "ROLE_ADMIN > ROLE_MANAGER"
        - "ROLE_MANAGER > ROLE_USER"
```

## JwtService

Handles token validation and parsing:

```java
// Validation — called by the filter
boolean isValid = jwtService.validateToken(token);

// Parsing — extracts user details
AuthenticatedUser user = jwtService.parseToken(token);
// user.id()          → UUID (from JWT subject)
// user.email()       → String (from 'email' claim)
// user.roles()       → Set<String> (from 'roles' claim)
// user.permissions() → Set<String> (from 'permissions' claim)
```

Validation checks:
- JWT signature (HMAC-SHA with configured secret key)
- Issuer matches configured issuer
- Expiration not passed
- Token is well-formed

On failure, logs a warning with the specific failure reason (expired, malformed, invalid signature, etc.) and returns `false`.

Null elements in `roles` or `permissions` JWT claims are silently filtered out (defensive against malformed tokens).

## JwtAuthenticationFilter (Service-Level)

The servlet filter in `security-starter`:

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String ROLE_PREFIX = "ROLE_";

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

            List<GrantedAuthority> authorities = new ArrayList<>();
            user.roles().forEach(role ->
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
            user.permissions().forEach(perm ->
                authorities.add(new SimpleGrantedAuthority(perm)));

            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, authorities)
            );
        }
        filterChain.doFilter(request, response);
    }
}
```

**Authority mapping model:**
- Roles from JWT are prefixed with `ROLE_` (e.g., JWT `"ADMIN"` → authority `ROLE_ADMIN`)
- Permissions from JWT are mapped directly (e.g., JWT `"order:read"` → authority `order:read`)

This means:
- Use `hasRole('ADMIN')` or `hasRole('MANAGER')` for role checks (Spring Security adds `ROLE_` internally)
- Use `hasAuthority('order:read')` for permission checks (exact match, no prefix)
- Use `hasAnyRole('ADMIN', 'MANAGER')` for multiple role checks
- Use `hasAnyAuthority('order:read', 'order:write')` for multiple permission checks

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
    Set<String> roles,
    Set<String> permissions
) {}
```

JWT claims mapping:
- `sub` (subject) → `id` (parsed as UUID)
- `email` → `email`
- `roles` → `roles` (List in JWT, converted to Set)
- `permissions` → `permissions` (List in JWT, converted to Set)

## Method-Level Authorization (@EnableMethodSecurity)

The `SecurityAutoConfiguration` enables `@EnableMethodSecurity`, allowing `@PreAuthorize` on controller methods:

```java
@GetMapping("/api/order")
@PreAuthorize("hasAuthority('order:read')")
public ResponseEntity<ApiResponse<List<Order>>> getOrders() { ... }

@PostMapping("/api/order/saga")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<UUID>> createOrder(...) { ... }
```

### Role Hierarchy (Optional)

Configure role inheritance via properties:
```yaml
acme:
  security:
    jwt:
      role-hierarchy:
        - "ROLE_ADMIN > ROLE_MANAGER"
        - "ROLE_MANAGER > ROLE_USER"
```

When configured, a user with `ADMIN` role automatically has `MANAGER` and `USER` privileges. The `RoleHierarchy` bean is only created when `role-hierarchy[0]` is set.

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
- **Roles are prefixed with `ROLE_`** before being mapped to `SimpleGrantedAuthority`. Use `hasRole('ADMIN')` (Spring adds prefix internally) or `hasAuthority('ROLE_ADMIN')` for role checks. Use `hasAuthority('order:read')` for permission checks (no prefix).
- **Permissions are mapped directly** (no prefix) — use the `resource:action` naming convention (e.g., `order:read`, `user:write`).
- The gateway propagates `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers downstream.
- When testing, the JWT secret in `application-test.yml` must be ≥64 characters.
- The gateway does **not** validate the JWT issuer (`requireIssuer`), but the service-level `JwtService` does. A token with a wrong issuer passes the gateway but fails at the service.
- For `@WebMvcTest` controller tests, use `@WithMockUser(authorities = "order:read")` for permission checks, and `@WithMockUser(roles = "ADMIN")` for role checks.
- **`@WebMvcTest` does NOT auto-load the starter's `SecurityAutoConfiguration`**. You must add `@Import(SecurityAutoConfiguration.class)` to the test class for `@PreAuthorize`, CSRF disable, and custom `SecurityFilterChain` to take effect. Without this import, Spring Boot's default security is used (CSRF enabled, no method security).
