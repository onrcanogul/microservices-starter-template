package com.template.starter.schedulerlock;

import com.template.starter.schedulerlock.property.SchedulerLockProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisAutoConfiguration.class,
                    SchedulerLockAutoConfiguration.class
            ));

    @Test
    void autoConfiguration_createsLockProviderBean() {
        contextRunner
                .withPropertyValues("spring.data.redis.host=localhost", "spring.data.redis.port=6379")
                .run(context -> {
                    assertThat(context).hasSingleBean(LockProvider.class);
                    assertThat(context).hasSingleBean(SchedulerLockProperties.class);
                });
    }

    @Test
    void autoConfiguration_disabledViaProperty_doesNotCreateBeans() {
        contextRunner
                .withPropertyValues("acme.scheduler-lock.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LockProvider.class);
                    assertThat(context).doesNotHaveBean(SchedulerLockProperties.class);
                });
    }

    @Test
    void autoConfiguration_customLockProviderOverridesDefault() {
        contextRunner
                .withPropertyValues("spring.data.redis.host=localhost", "spring.data.redis.port=6379")
                .withBean("lockProvider", LockProvider.class, () -> lockConfiguration -> {
                    throw new UnsupportedOperationException("Custom lock provider");
                })
                .run(context -> assertThat(context).hasSingleBean(LockProvider.class));
    }

    @Test
    void properties_defaultValues() {
        SchedulerLockProperties properties = new SchedulerLockProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultLockAtMost()).hasMinutes(10);
        assertThat(properties.getDefaultLockAtLeast()).hasSeconds(5);
        assertThat(properties.getKeyPrefix()).isEqualTo("shedlock:");
    }

    @Test
    void autoConfiguration_customProperties_appliedToBean() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379",
                        "acme.scheduler-lock.key-prefix=myapp:",
                        "acme.scheduler-lock.default-lock-at-most=PT1H",
                        "acme.scheduler-lock.default-lock-at-least=PT10S")
                .run(context -> {
                    SchedulerLockProperties props = context.getBean(SchedulerLockProperties.class);
                    assertThat(props.getKeyPrefix()).isEqualTo("myapp:");
                    assertThat(props.getDefaultLockAtMost()).hasHours(1);
                    assertThat(props.getDefaultLockAtLeast()).hasSeconds(10);
                });
    }
}
