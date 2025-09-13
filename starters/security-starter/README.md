# Security Starter

### Purpose
The **Security Starter** provides a ready-to-use **JWT-based authentication and authorization** setup for Spring Boot microservices.  
It eliminates repetitive security configuration in each service by centralizing:

- HTTP security rules (public vs protected endpoints)
- JWT filter chain integration
- Stateless session management
- Method-level security (`@PreAuthorize`, `@Secured`)

This ensures **consistent and secure authentication/authorization** across all services.

---

### How It Works
1. **JwtAuthenticationFilter** validates incoming JWT tokens.
2. **SecurityFilterChain** defines request authorization rules.
3. **SessionManagement** is set to **STATELESS** (no HTTP sessions).
4. **Method Security** is enabled globally with `@EnableMethodSecurity`.

---

### AutoConfiguration
The starter automatically registers the `SecurityConfig` and JWT filter integration:

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll() // Public endpoints
                .anyRequest().authenticated()            // Everything else requires JWT
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### Properties
```yaml
security:
  jwt:
    secret: my-secret-key
    expiration: 3600   # token expiration in seconds
    issuer: template
