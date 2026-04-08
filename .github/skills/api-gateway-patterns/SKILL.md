---
name: api-gateway-patterns
description: "Use when modifying the API Gateway: adding routes, configuring rate limiting, customizing JWT validation, adding global filters, or debugging gateway behavior. Covers the reactive WebFlux architecture, JwtAuthFilter with header propagation, Redis rate limiting, CORS config, and the critical difference between gateway (reactive) and service (servlet) filters."
---

# API Gateway Patterns

The API gateway is a **reactive** Spring Cloud Gateway application (WebFlux). It handles JWT validation, rate limiting, CORS, and request routing. Understanding that the gateway is reactive is critical — it uses completely different abstractions from the servlet-based service filters.

## Reactive vs Servlet

| Aspect | API Gateway | Service (via security-starter) |
|--------|-------------|-------------------------------|
| Runtime | WebFlux (Netty) | Servlet (Tomcat) |
| Filter type | `GlobalFilter` | `OncePerRequestFilter` |
| Exchange | `ServerWebExchange` | `HttpServletRequest` |
| Response | `Mono<Void>` | void (blocking) |
| JWT lib | `io.jsonwebtoken` directly | `security-starter` JwtService |
| Security | Manual JWT parsing | Spring Security `SecurityFilterChain` |

Never import `security-starter` into the gateway module — it's servlet-based and incompatible.

## JwtAuthFilter

The gateway's JWT filter is a `GlobalFilter` that runs on every request:

```java
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    // SecretKey from ${acme.security.jwt.secret}

    private static final Set<String> OPEN_PATHS = Set.of(
        "/auth/login", "/auth/register", "/auth/refresh"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        if (OPEN_PATHS.contains(path)) return chain.filter(exchange);

        // Extract Bearer token, validate JWT, mutate request with user headers
        ServerWebExchange mutated = exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Email",
                    claims.get("email", String.class) != null
                        ? claims.get("email", String.class) : "")
                .build())
            .build();
        return chain.filter(mutated);
    }
}
```

Key behaviors:
- **Open paths** bypass JWT validation entirely — defined as a static `Set<String>`
- On success: injects `X-User-Id` and `X-User-Email` headers into the downstream request
- On failure: returns reactive JSON error response with appropriate HTTP status (401/500)
- Error responses are manually written to `ServerHttpResponse` via `DataBuffer`

### Adding Open Paths

Modify the `OPEN_PATHS` set in `JwtAuthFilter`:

```java
private static final Set<String> OPEN_PATHS = Set.of(
    "/auth/login",
    "/auth/register",
    "/auth/refresh",
    "/public/health"    // add new open paths here
);
```

## Route Configuration

Routes are defined declaratively in `application.yml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: example-service
          uri: lb://example-service              # Eureka load-balanced
          predicates:
            - Path=/example/**                   # incoming path pattern
          filters:
            - RewritePath=/example/(?<segment>.*), /api/example/${segment}
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: ${RATE_LIMIT_RPS:10}
                redis-rate-limiter.burstCapacity: ${RATE_LIMIT_BURST:20}
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"
```

### Route anatomy:
- `uri: lb://<service-name>` — resolves via Eureka service discovery
- `Path` predicate — matches incoming request path
- `RewritePath` — rewrites the URL before forwarding (strips gateway prefix)
- `RequestRateLimiter` — Redis-backed per-IP rate limiting

### Adding a new route:
1. Define the route in `spring.cloud.gateway.routes`
2. Use `lb://` prefix for Eureka-registered services
3. Add `RewritePath` to strip the gateway path prefix
4. Include `RequestRateLimiter` for protection

## Rate Limiting

Rate limiting uses Redis with an IP-based key resolver:

```java
@Configuration
public class RateLimiterConfig {
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown"
        );
    }
}
```

Configuration via environment variables:
- `RATE_LIMIT_RPS` — sustained requests per second (default: 10)
- `RATE_LIMIT_BURST` — burst capacity (default: 20)
- Redis connection: `REDIS_HOST` (default: localhost), `REDIS_PORT` (default: 6379)

## CORS Configuration

Global CORS is configured in YAML:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "${CORS_ALLOWED_ORIGINS:http://localhost:3000}"
            allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
            allowed-headers: "*"
```

The `CORS_ALLOWED_ORIGINS` env var controls allowed origins in production.

## LoggingFilter

A simple global filter logs request URI and response status:

```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("[LOG] Request: {}", exchange.getRequest().getURI());
        return chain.filter(exchange).then(
            Mono.fromRunnable(() ->
                log.info("[LOG] Response status: {}", exchange.getResponse().getStatusCode()))
        );
    }

    @Override
    public int getOrder() { return 0; }  // runs after JwtAuthFilter (-1)
}
```

## Writing Error Responses (Reactive)

In the gateway, error responses must be written reactively:

```java
private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    byte[] bytes;
    try {
        bytes = OBJECT_MAPPER.writeValueAsBytes(Map.of("error", err));
    } catch (Exception e) {
        bytes = "{\"error\":\"Authentication error\"}".getBytes(StandardCharsets.UTF_8);
    }
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    response.getHeaders().add("Content-Type", "application/json");
    return response.writeWith(Mono.just(buffer));
}
```

The try-catch fallback ensures the gateway never fails to return a response body. Do not throw exceptions from gateway filters — write error buffers directly.

## Gotchas

- Gateway and services validate JWT independently. If you change the secret, update both `acme.security.jwt.secret` configs.
- The `@Ordered` interface on filters controls execution order. `JwtAuthFilter` defaults to `-1` (runs before `LoggingFilter` at `0`).
- Eureka must be running before the gateway can resolve `lb://` URIs.
- Redis must be running for rate limiting to work. If Redis is down, requests fail with 500.
