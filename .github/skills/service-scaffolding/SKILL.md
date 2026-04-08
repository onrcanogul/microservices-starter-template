---
name: service-scaffolding
description: "Use when creating a new microservice, adding a service module, or wiring starters into a service. Covers the exact layered package structure (api/application/domain/infrastructure), the 8-step creation checklist, config server integration, gateway route setup, Flyway migrations, starter wiring (outbox/inbox/kafka), and POM configuration."
---

# Service Scaffolding

New microservices follow a structured creation process. The `example-service` is the reference implementation — copy it and adjust. This skill covers the exact steps, package layout, and wiring patterns.

## Package Structure

Every service uses this layered layout under `com.template.microservices.<service-name>/`:

```
com.template.microservices.<name>/
├── <Name>ServiceApplication.java              # @SpringBootApplication entry point
├── api/
│   └── controller/
│       └── <Entity>Controller.java            # REST endpoints — thin delegates
├── application/
│   └── service/
│       ├── <entity>/
│       │   ├── <Entity>Service.java           # Interface
│       │   └── impl/
│       │       └── <Entity>ServiceImpl.java   # Implementation with business logic
│       └── inbox/
│           └── <Name>InboxProcessor.java      # Inbox event processor
├── domain/
│   └── entity/
│       └── <Entity>.java                      # JPA entity
├── infrastructure/
│   ├── configuration/
│   │   └── TopicConfig.java                   # Kafka topic beans
│   ├── messaging/
│   │   ├── <Event>Event.java                  # Event records (implements Event)
│   │   ├── consumer/
│   │   │   └── <Entity>Consumer.java          # Kafka consumers → inbox
│   │   └── processor/
│   │       └── <Entity>CreatedProducer.java   # Outbox producers
│   └── repository/
│       └── <Entity>Repository.java            # Spring Data JPA
```

## The 8-Step Creation Checklist

### 1. Copy example-service

```bash
cp -r services/example-service services/<new-service>
```

### 2. Update identifiers

In `pom.xml`:
```xml
<artifactId><new-service></artifactId>
```

In `application.yml`:
```yaml
spring:
  application:
    name: <new-service>
```

Rename the main class: `<Name>ServiceApplication.java` with `@SpringBootApplication`.

### 3. Add to parent POM modules

In `services/pom.xml`:
```xml
<modules>
    <module>example-service</module>
    <module><new-service></module>
</modules>
```

### 4. Create config server config

Create `infra/config-server/src/main/resources/configs/<new-service>/application.yml`:

```yaml
acme:
  messaging:
    kafka:
      max-attempts: 5
      backoff-ms: 200
      dlt-suffix: ".DLT"
      topic-prefix: ""

logging:
  pattern:
    level: "%5p [trace=%X{traceId:-} span=%X{spanId:-}]"
```

### 5. Add gateway route

In `infra/api-gateway/src/main/resources/application.yml`, add under `spring.cloud.gateway.routes`:

```yaml
- id: <new-service>                # matches spring.application.name
  uri: lb://<new-service>           # Eureka-resolved name
  predicates:
    - Path=/<short-name>/**         # public path alias (e.g., /example, /orders)
  filters:
    - RewritePath=/<short-name>/(?<segment>.*), /api/<resource-prefix>/${segment}
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: ${RATE_LIMIT_RPS:10}
        redis-rate-limiter.burstCapacity: ${RATE_LIMIT_BURST:20}
        redis-rate-limiter.requestedTokens: 1
        key-resolver: "#{@ipKeyResolver}"
```

The `Path` predicate uses a **short alias** (not the full service name). The `RewritePath` target includes the resource prefix so the downstream service receives the full API path (e.g., `/example/order/1` → `/api/example/order/1`).

### 6. Create Flyway migrations

Place in `src/main/resources/db/migration/`:

- `V1__create_<entity>_table.sql` — business table with audit columns
- `V2__create_outbox_table.sql` — standard outbox schema (see outbox-inbox-pattern skill)
- `V3__create_inbox_table.sql` — standard inbox schema

Business table template:
```sql
CREATE TABLE <entity> (
    id          BIGSERIAL       PRIMARY KEY,
    -- business columns
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  VARCHAR(255)    NOT NULL,
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by  VARCHAR(255),
    deleted_at  TIMESTAMPTZ
);
```

### 7. Create CI/CD workflows

Copy from `.github/workflows/example-service-ci.yml` and `.github/workflows/example-service.cd.yml`:

- Update workflow name
- Update path triggers to `services/<new-service>/**`
- Update Maven `-pl services/<new-service>` in build command

### 8. Add Docker and K8s manifests

- `services/<new-service>/Dockerfile`
- `infra/k8s/<new-service>.yaml` with deployment, service, probes, resource limits

## Wiring Patterns

### Controller (thin delegate)

```java
@RestController
@RequestMapping("/api/<entity>")
public class EntityController {
    private final EntityService service;

    public EntityController(EntityService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EntityDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EntityDto>> create(@Valid @RequestBody CreateEntityRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(service.create(req)));
    }
}
```

### Service (business logic)

```java
@Service
public class EntityServiceImpl implements EntityService {
    private final EntityRepository repository;
    private final EntityCreatedProducer producer;  // outbox-backed producer

    @Override
    @Transactional
    public EntityDto create(CreateEntityRequest req) {
        Entity entity = repository.save(mapToEntity(req));
        // Call producer inside @Transactional to guarantee atomicity via outbox
        producer.process(new EntityCreatedEvent(entity.getId()));
        return mapToDto(entity);
    }

    @Override
    public EntityDto getById(Long id) {
        return repository.findById(id)
            .map(this::mapToDto)
            .orElseThrow(() -> BusinessException.of(
                StandardErrorCodes.NOT_FOUND, "Entity not found: " + id));
    }
}
```

### application.yml template

```yaml
spring:
  application:
    name: <new-service>
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/appdb}
    username: ${DB_USER:app}
    password: ${DB_PASSWORD:app}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:19092}
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: latest
      properties:
        spring.json.trusted.packages: "*"  # required for EventWrapper deserialization
    producer:
      acks: all
      properties:
        enable.idempotence: true

eureka:
  client:
    service-url:
      defaultZone: http://discovery-service:8761/eureka

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

acme:
  security:
    jwt:
      secret: ${JWT_SECRET}
```

## JPA Entity Pattern

Entities use `@Getter @Setter` (never `@Data`). Include audit columns for soft delete:

```java
@Entity
@Getter @Setter
@Table(name = "<entity>")
public class MyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Business fields

    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private boolean isDeleted;
    private String deletedBy;
    private Instant deletedAt;
}
```

For auditing interfaces (`IInsertAuditing`, `IUpdateAuditing`, `ISoftDelete`), implement them when you want the persistence-starter's JPA auditing to manage the fields automatically.
