# api-gateway

Reactive Spring Cloud Gateway. Single entry point; validates JWTs, enforces Redis rate limits, routes via Eureka `lb://`. Package `com.template.gateway`.

## Filters / routes
| Component | Role |
|-----------|------|
| `filter/JwtAuthFilter` (order -1) | Validates `Bearer` token vs `acme.security.jwt.secret`; open paths `/auth/login`, `/auth/register`, `/auth/refresh`; propagates `X-User-Id` (sub), `X-User-Email`, `X-User-Roles`; errors → JSON `{"error":...}` 401/500 |
| `filter/LoggingFilter` (order 0) | Logs request URI + response status |
| `config/RateLimiterConfig` | `ipKeyResolver` bean (per-client-IP key) |

Route `example-service`: `Path=/example/**` → `lb://example-service`, rewrite `/example/(seg)`→`/api/example/(seg)`, `RequestRateLimiter` (replenishRate `RATE_LIMIT_RPS:10`, burstCapacity `RATE_LIMIT_BURST:20`, tokens 1). CORS `/**`: origins `CORS_ALLOWED_ORIGINS:http://localhost:3000`, methods GET/POST/PUT/DELETE/OPTIONS. Redis `REDIS_HOST:localhost:REDIS_PORT:6379`.

## Run
Port `8000`. Needs `JWT_SECRET`, Redis, Eureka. Start config-server + discovery-service + backends first; gateway last (route targets must be registered).
```bash
mvn -pl infra/api-gateway -am spring-boot:run
```
