package com.template.starter.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.starter.idempotency.filter.IdempotencyFilter;
import com.template.starter.idempotency.property.IdempotencyProperties;
import com.template.starter.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class));

    @Test
    void autoConfiguration_registersAllBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyService.class);
            assertThat(context).hasSingleBean(IdempotencyFilter.class);
            assertThat(context).hasSingleBean(IdempotencyProperties.class);
        });
    }

    @Test
    void autoConfiguration_disabledViaProperty() {
        contextRunner
                .withPropertyValues("acme.idempotency.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempotencyService.class);
                    assertThat(context).doesNotHaveBean(IdempotencyFilter.class);
                });
    }

    @Test
    void autoConfiguration_allowsCustomServiceOverride() {
        contextRunner
                .withBean("idempotencyService", IdempotencyService.class,
                        () -> mock(IdempotencyService.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyService.class);
                });
    }

    @Test
    void properties_haveDefaults() {
        contextRunner.run(context -> {
            IdempotencyProperties props = context.getBean(IdempotencyProperties.class);
            assertThat(props.getHeaderName()).isEqualTo("Idempotency-Key");
            assertThat(props.getKeyPrefix()).isEqualTo("idempotency:");
            assertThat(props.getDefaultTtlSeconds()).isEqualTo(86400);
            assertThat(props.getLockTtlSeconds()).isEqualTo(30);
            assertThat(props.isEnabled()).isTrue();
        });
    }

    @Test
    void properties_customValues() {
        contextRunner
                .withPropertyValues(
                        "acme.idempotency.header-name=X-Request-Id",
                        "acme.idempotency.key-prefix=custom:",
                        "acme.idempotency.default-ttl-seconds=3600",
                        "acme.idempotency.lock-ttl-seconds=10"
                )
                .run(context -> {
                    IdempotencyProperties props = context.getBean(IdempotencyProperties.class);
                    assertThat(props.getHeaderName()).isEqualTo("X-Request-Id");
                    assertThat(props.getKeyPrefix()).isEqualTo("custom:");
                    assertThat(props.getDefaultTtlSeconds()).isEqualTo(3600);
                    assertThat(props.getLockTtlSeconds()).isEqualTo(10);
                });
    }
}
