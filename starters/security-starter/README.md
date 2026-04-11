# Security Starter

### Purpose
The **Security Starter** provides a ready-to-use **JWT-based authentication and authorization** setup for Spring Boot microservices.  
It eliminates repetitive security configuration in each service by centralizing:

- HTTP security rules (public vs protected endpoints)
- JWT filter chain integration with role + permission extraction
- Stateless session management
- Method-level security (`@PreAuthorize`, `@Secured`, `@RolesAllowed`)
- Configurable role hierarchy
- `ROLE_` prefix convention for Spring Security compatibility

This ensures **consistent and secure authentication/authorization** across all services.

---

### How It Works
1. **JwtAuthenticationFilter** validates incoming JWT tokens and maps roles/permissions to `GrantedAuthority`.
2. **SecurityFilterChain** defines request authorization rules (public paths, authenticated endpoints).
3. **SessionManagement** is set to **STATELESS** (no HTTP sessions).
4. **Method Security** is enabled globally with `@EnableMethodSecurity`, supporting `@PreAuthorize`.
5. **RoleHierarchy** (optional) allows defining role inheritance (e.g., ADMIN implies MODERATOR implies USER).

---

### JWT Claims Mapping

| JWT Claim | AuthenticatedUser field | GrantedAuthority | Access Pattern |
|-----------|------------------------|-----------------|----------------|
| `sub` | `id` (UUID) | — | `auth.getPrincipal().id()` |
| `email` | `email` (String) | — | `auth.getPrincipal().email()` |
| `roles` | `roles` (Set\<String>) | `ROLE_{name}` | `hasRole('ADMIN')` or `hasAuthority('ROLE_ADMIN')` |
| `permissions` | `permissions` (Set\<String>) | `{name}` (no prefix) | `hasAuthority('order:read')` |

Roles are prefixed with `ROLE_` to follow Spring Security conventions. Permissions are mapped directly without prefix.

---

### Usage

#### 1. Add dependency
```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>security-starter</artifactId>
</dependency>
```

#### 2. Configure properties
```yaml
acme:
  security:
    jwt:
      secret: ${JWT_SECRET}              # ≥64 characters, NEVER hardcode
      expiration-ms: 3600000             # 1 hour (optional, default: 3600000)
      issuer: acme-enterprise            # JWT issuer claim (optional)
      public-paths:                      # paths that skip authentication
        - /api/public/**
        - /webhook/**
      role-hierarchy:                    # optional role inheritance
        - "ROLE_ADMIN > ROLE_MODERATOR"
        - "ROLE_MODERATOR > ROLE_USER"
```

#### 3. Use `@PreAuthorize` on endpoints
```java
@GetMapping("/api/orders")
@PreAuthorize("hasAuthority('order:read')")
public ResponseEntity<ApiResponse<List<Order>>> getOrders() { ... }

@PostMapping("/api/orders")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<Order>> createOrder(@RequestBody CreateOrderRequest request) { ... }

@DeleteMapping("/api/orders/{id}")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('order:delete')")
public ResponseEntity<Void> deleteOrder(@PathVariable Long id) { ... }
```

#### 4. Access authenticated user in controllers
```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserDto>> getProfile(Authentication auth) {
    AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
    // user.id(), user.email(), user.roles(), user.permissions()
}
```

---

### Role Hierarchy

When configured, role hierarchy allows higher roles to inherit lower ones:

```yaml
acme:
  security:
    jwt:
      role-hierarchy:
        - "ROLE_ADMIN > ROLE_MODERATOR"
        - "ROLE_MODERATOR > ROLE_USER"
```

With this hierarchy, a user with `ADMIN` role can access endpoints requiring `MODERATOR` or `USER` roles.

If `role-hierarchy` is not configured, no hierarchy bean is created and each role is independent.

---

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `acme.security.jwt.secret` | String | **required** | HMAC-SHA signing key (≥64 chars) |
| `acme.security.jwt.expiration-ms` | long | 3600000 | Token expiration in milliseconds |
| `acme.security.jwt.issuer` | String | `acme-enterprise` | JWT issuer claim |
| `acme.security.jwt.public-paths` | String[] | — | Paths that skip authentication |
| `acme.security.jwt.role-hierarchy` | String[] | — | Role hierarchy definitions |

---

### Default Public Paths (always permitted)
- `/v3/api-docs/**` — OpenAPI documentation
- `/swagger-ui/**` — Swagger UI
- `/swagger-ui.html`
- `/actuator/health` — health probe
- `/actuator/info`

---

### Overriding Beans

All beans use `@ConditionalOnMissingBean`. Override any bean by defining your own:

```java
@Bean
public SecurityFilterChain customSecurityFilterChain(HttpSecurity http, ...) throws Exception {
    // your custom security configuration
}

@Bean
public RoleHierarchy customRoleHierarchy() {
    // your custom hierarchy
}
```

---

### Testing with Security

Use `@WithMockUser` in `@WebMvcTest` tests:

```java
@WebMvcTest(OrderController.class)
@TestPropertySource(properties = "acme.security.jwt.secret=test-secret-that-is-at-least-64-characters-long-for-hmac-sha256-signing-key")
class OrderControllerTest {

    @Test
    @WithMockUser(authorities = "order:read")
    void get_shouldReturnOrders() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_adminOnly_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
```