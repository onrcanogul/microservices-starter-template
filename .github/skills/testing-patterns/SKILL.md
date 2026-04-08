---
name: testing-patterns
description: "Use when writing unit tests, controller tests, or integration tests. Covers JUnit 5 + Mockito conventions, @WebMvcTest with @MockitoBean (not deprecated @MockBean), Testcontainers base classes (AbstractPostgresIntegrationTest, AbstractKafkaIntegrationTest, AbstractFullIntegrationTest), application-test.yml setup, ApiResponse assertion patterns, and test naming."
---

# Testing Patterns

This project provides three Testcontainers base classes for integration tests and follows strict conventions for unit and controller tests. Use `@MockitoBean` (not `@MockBean`), always assert `ApiResponse` structure, and pick the right base class for your integration test.

## Test Types and When to Use

| Type | Annotation | Base Class | Use For |
|------|-----------|------------|---------|
| Unit test | `@ExtendWith(MockitoExtension.class)` | None | Service logic in isolation |
| Controller test | `@WebMvcTest(Controller.class)` | None | REST endpoint validation |
| Postgres integration | `@SpringBootTest` | `AbstractPostgresIntegrationTest` | Repository/JPA tests |
| Kafka integration | `@SpringBootTest` | `AbstractKafkaIntegrationTest` | Consumer/producer tests |
| Full integration | `@SpringBootTest` | `AbstractFullIntegrationTest` | End-to-end with DB + Kafka |

## Test Naming Convention

```
methodName_condition_expectedResult
```

Examples:
- `getById_shouldReturnOrder_whenExists`
- `getById_shouldThrowBusinessException_whenNotFound`
- `get_shouldReturnEmptyList`
- `delete_shouldSoftDeleteOrder`

## Unit Tests

Use `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`. Assert with AssertJ:

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository repository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.setId(1L);
        sampleOrder.setSku("SKU-001");
        sampleOrder.setAmount(5);
        sampleOrder.setCreatedAt(Instant.now());
        sampleOrder.setCreatedBy("test-user");
    }

    @Test
    void getById_shouldReturnOrder_whenExists() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        Order result = orderService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSku()).isEqualTo("SKU-001");
    }

    @Test
    void getById_shouldThrowBusinessException_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order not found with id: 99");
    }
}
```

## Controller Tests

Use `@WebMvcTest` with `@MockitoBean` — **not** the deprecated `@MockBean`:

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private EventPublisher eventPublisher;  // mock all injected dependencies

    @Test
    void get_shouldReturnOrders() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setSku("SKU-001");
        order.setAmount(5);
        order.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        order.setCreatedBy("test-user");

        when(orderService.get()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data[0].amount").value(5));
    }

    @Test
    void get_shouldReturnEmptyList() throws Exception {
        when(orderService.get()).thenReturn(List.of());

        mockMvc.perform(get("/api/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
```

### ApiResponse Assertion Pattern

Since all endpoints return `ApiResponse<T>`, always verify the wrapper structure. The actual example-service tests skip the `$.success` assertion, but including it is recommended:
- `jsonPath("$.success").value(true)` for success
- `jsonPath("$.data")` for the payload
- `jsonPath("$.error.code")` for error responses

## Integration Test Base Classes

All base classes are in `common-test` module (`com.template.test` package). Add it as a test-scoped dependency:

```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>common-test</artifactId>
    <scope>test</scope>
</dependency>
```

### AbstractPostgresIntegrationTest

Extend this class to get a PostgreSQL 16 container:

```java
@SpringBootTest
@ActiveProfiles("test")
class OrderRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderRepository repository;

    @Test
    void save_shouldPersistOrder() {
        // Test with real PostgreSQL
    }
}
```

Container config: `postgres:16-alpine`, database `testdb`, user/password `test/test`.

### AbstractKafkaIntegrationTest

Extend this class to get an Apache Kafka 3.7 container:

```java
@SpringBootTest
@ActiveProfiles("test")
class OrderConsumerIntegrationTest extends AbstractKafkaIntegrationTest {
    // Test with real Kafka broker
}
```

Container: `apache/kafka:3.7.0`

### AbstractFullIntegrationTest

Extend this class to get both PostgreSQL and Kafka containers:

```java
@SpringBootTest
@ActiveProfiles("test")
class OrderFlowIntegrationTest extends AbstractFullIntegrationTest {
    // Test with both real DB and Kafka
}
```

All base classes use `@DynamicPropertySource` to inject container connection details into Spring context.

## application-test.yml

Every service needs a test configuration at `src/test/resources/application-test.yml`:

```yaml
spring:
  application:
    name: <service-name>-test
  config:
    import: optional:configserver:     # prevents config server connection failure
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
  kafka:
    bootstrap-servers: localhost:9092

eureka:
  client:
    enabled: false

acme:
  security:
    jwt:
      secret: test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha256-signing
```

Critical settings:
- `spring.config.import: optional:configserver:` — prevents test failure when config server is unavailable
- `eureka.client.enabled: false` — disables service discovery in tests
- `acme.security.jwt.secret` — must be **at least 64 characters** (SecurityProperties validates this in its compact constructor)
- H2 with `create-drop` for controller/service tests; Testcontainers for integration tests

## Gotchas

- `@MockitoBean` is from `org.springframework.test.context.bean.override.mockito` — the correct import for Spring Boot 3.4+
- Mock **all** dependencies injected into the controller in `@WebMvcTest`, including `EventPublisher`
- Testcontainers containers are `static final` in the base classes — shared across test methods in a class
- The H2 `application-test.yml` and Testcontainers base classes serve different purposes: use H2 for `@SpringBootTest` tests that don't extend a Testcontainers base class (`@WebMvcTest` does not load a datasource). Use Testcontainers for integration tests needing real database/Kafka behavior.
- `@ActiveProfiles("test")` activates the test profile; combine with `@SpringBootTest` for integration tests
