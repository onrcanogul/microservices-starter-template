# security-starter

Stateless JWT security: authentication filter, JWT parsing/validation, BCrypt encoder, method security, and a default `SecurityFilterChain`. Config under `acme.security.jwt.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `SecurityAutoConfiguration` | `@EnableMethodSecurity`; wires filter chain, encoder, JWT service |
| `SecurityProperties` | `@ConfigurationProperties("acme.security.jwt")` record; validates secret (≥64 chars) |
| `JwtService` | `validateToken(token)`, `parseToken(token) -> AuthenticatedUser` |
| `AuthenticatedUser` | `record(UUID id, String email, Set<String> roles, Set<String> permissions)` |
| `JwtAuthenticationFilter` | `OncePerRequestFilter`; sets `SecurityContext` from bearer token |
| `SecurityFilterChain` | Stateless; CSRF off; permits swagger + actuator health/info + `public-paths`; everything else authenticated |
| `RoleHierarchy` | Built from `role-hierarchy` (only if `role-hierarchy[0]` set) |
| `PasswordEncoder` | `BCryptPasswordEncoder` |
| `AuthenticationEntryPoint` / `AccessDeniedHandler` | 401 / 403 responders |

## Config (`acme.security.jwt.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `secret` | (required, ≥64 chars) | HMAC signing key; blank/short throws at startup |
| `expiration-ms` | `3600000` | Token TTL |
| `issuer` | `acme-enterprise` | Token issuer |
| `public-paths` | — | Extra unauthenticated path patterns |
| `role-hierarchy` | — | Lines like `ROLE_ADMIN > ROLE_USER`; enables `RoleHierarchy` |

## Depends on
`common-core`, Spring Security, JJWT.

## See
skill `security-jwt-flow`
