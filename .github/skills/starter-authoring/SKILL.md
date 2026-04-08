---
name: starter-authoring
description: "Use when creating a new Spring Boot auto-configuration starter module. Covers @AutoConfiguration with conditional annotations, *Properties classes under acme.* namespace, marker class pattern for component scanning, META-INF registration, nested config classes, and POM conventions. Also use when modifying existing starters."
---

# Starter Authoring

This project's shared infrastructure is delivered as Spring Boot auto-configuration starters. Each starter follows a rigid structure that ensures they compose correctly, allow overrides, and activate only when appropriate.

## Directory Structure

```
starters/<name>-starter/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/template/starter/<name>/
    │   ├── <Name>AutoConfiguration.java
    │   ├── <Name>StarterMarker.java          # optional — for component scan
    │   └── property/
    │       └── <Name>Properties.java
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

The package convention has two patterns:
- `com.template.persistence` — persistence-starter
- `com.template.kafka` — kafka-starter
- `com.template.observer` — observability-starter (historical)
- `com.template.starter.<name>` — all newer starters (outbox, inbox, security, cache, resilience)

Use `com.template.starter.<name>` for new starters.

## AutoConfiguration Class

Use `@AutoConfiguration` (not `@Configuration`). Every bean gets `@ConditionalOnMissingBean` so services can override:

```java
@AutoConfiguration
@ConditionalOnClass(SomeRequiredClass.class)
@EnableConfigurationProperties(MyProperties.class)
public class MyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MyService myService(MyProperties props) {
        return new MyService(props);
    }
}
```

### Conditional Annotations

| Annotation | When to Use |
|-----------|-------------|
| `@ConditionalOnMissingBean` | Every bean — allows service-level overrides |
| `@ConditionalOnClass` | Starter depends on optional library being on classpath |
| `@ConditionalOnProperty` | Feature toggle, e.g., `acme.my.enabled` |
| `@ConditionalOnBean` | Bean depends on another bean existing |

For feature toggles, use `matchIfMissing = true` so the starter is active by default:

```java
@ConditionalOnProperty(prefix = "acme.mystarter", name = "enabled",
                        havingValue = "true", matchIfMissing = true)
```

### Enabling Annotations

Some starters activate Spring features that affect the whole application context. Document these in the starter's README:

| Annotation | Used In | Effect |
|-----------|---------|--------|
| `@EnableScheduling` | outbox-starter, inbox-starter | Enables `@Scheduled` polling. Only one starter needs this per context. |
| `@EnableCaching` | cache-starter | Enables `@Cacheable`/`@CacheEvict` proxy support. |
| `@EnableJpaAuditing` | persistence-starter (nested config) | Activates `@CreatedDate`, `@LastModifiedDate`, `AuditorAware`. |

### Nested AutoConfiguration

For logically grouped sub-features, use nested static `@AutoConfiguration` inner classes:

```java
@AutoConfiguration
public class MyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MainService mainService() { ... }

    @AutoConfiguration
    @ConditionalOnProperty(prefix = "acme.my", name = "sub-feature-enabled",
                            havingValue = "true", matchIfMissing = true)
    static class SubFeatureConfig {
        @Bean
        @ConditionalOnMissingBean
        public SubFeature subFeature() { ... }
    }
}
```

See `PersistenceAutoConfiguration.JpaAuditingConfig` for a working example.

## Marker Class Pattern

When using `@ComponentScan`, `@EntityScan`, or `@EnableJpaRepositories`, point them at a marker class instead of a string package:

```java
public final class MyStarterMarker {}

@AutoConfiguration
@ComponentScan(basePackageClasses = MyStarterMarker.class)
@EnableJpaRepositories(basePackageClasses = MyStarterMarker.class)
@EntityScan(basePackageClasses = MyStarterMarker.class)
public class MyAutoConfiguration { ... }
```

This is type-safe and survives refactoring. Both `outbox-starter` and `inbox-starter` use this pattern.

## Properties Class

All properties live under the `acme.*` namespace:

```java
@ConfigurationProperties(prefix = "acme.mystarter")
public class MyProperties {
    private boolean enabled = true;
    private int batchSize = 50;
    private Duration timeout = Duration.ofSeconds(30);

    // Nested config group
    private Retry retry = new Retry();

    public static class Retry {
        private int maxAttempts = 3;
        private long backoffMs = 200;
        // getters + setters
    }

    // getters + setters for all fields
}
```

For immutable configuration (especially security), use a record with constructor validation:

```java
@ConfigurationProperties(prefix = "acme.security.jwt")
public record SecurityProperties(String secret, long expirationMs) {
    public SecurityProperties {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("...");
    }
}
```

Established namespace prefixes:

| Prefix | Starter |
|--------|---------|
| `acme.persistence.*` | persistence-starter |
| `acme.security.jwt.*` | security-starter |
| `acme.obs.*` | observability-starter |
| `acme.messaging.kafka.*` | kafka-starter |
| `acme.cache.*` | cache-starter |
| `acme.resilience.*` | resilience-starter |
| `acme.outbox.*` | outbox-starter | ⚠️ `@ConditionalOnProperty` uses `outbox.scheduler` (no `acme.` prefix — known inconsistency) |
| `acme.inbox.*` | inbox-starter |
| `acme.web.error.*` | common-web |

Use a `*Properties` class, not `@Value` annotations. Register with `@EnableConfigurationProperties`. Note: outbox-starter and inbox-starter currently use `@Value` — this should be migrated to `*Properties` classes.

## META-INF Registration

Create this file (`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):

```
com.template.starter.mystarter.MyAutoConfiguration
```

One fully-qualified class name per line. This is how Spring Boot discovers the auto-configuration.

## POM Conventions

Parent: `starters/pom.xml`. Dependencies:
- Commons modules (common-core, common-messaging, etc.) — allowed
- Other starters — avoid cross-dependencies. Composite starters (outbox-starter, inbox-starter) that compose persistence + messaging are the exception. Document the rationale in the README when this occurs. Simple starters must never depend on other starters.
- Spring Boot starters (spring-boot-starter-data-jpa, etc.) — use `optional` scope for conditional classpath activation

```xml
<parent>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>starters</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>

<artifactId>my-starter</artifactId>

<dependencies>
    <dependency>
        <groupId>com.acme.enterprise</groupId>
        <artifactId>common-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

## README.md

Every starter includes a README documenting:
1. Purpose and what it auto-configures
2. All configuration properties with defaults
3. Usage example (which dependency to add, minimal config)
4. How to override default beans

## Dependency Flow

```
commons → starters → services
```

This is a strict one-direction rule:
- Commons modules have no dependencies on starters or services
- Starters depend on commons, and avoid cross-dependencies (except composite starters like outbox/inbox)
- Services depend on any combination of starters + commons

## Existing Starters Reference

When creating a new starter, use these as models:
- **Simple config**: `resilience-starter` — just logging consumers and properties
- **JPA + scheduling**: `outbox-starter` — entities, repositories, scheduled processing
- **Security chain**: `security-starter` — filter chain with conditional beans
- **Redis integration**: `cache-starter` — external dependency with JSON serialization
