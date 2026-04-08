package com.template.starter.schedulerlock;

import com.template.starter.schedulerlock.property.SchedulerLockProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Auto-configuration for distributed scheduler locking via ShedLock + Redis.
 * <p>
 * Activates when:
 * <ul>
 *   <li>{@code RedisConnectionFactory} is on the classpath (spring-data-redis)</li>
 *   <li>{@code LockProvider} (shedlock-core) is on the classpath</li>
 *   <li>{@code acme.scheduler-lock.enabled} is not explicitly set to {@code false}</li>
 * </ul>
 * <p>
 * Creates a Redis-backed {@link LockProvider} that ShedLock uses to coordinate
 * {@code @SchedulerLock}-annotated methods across multiple application instances.
 * <p>
 * The {@code @EnableSchedulerLock} annotation activates ShedLock's
 * {@link net.javacrumbs.shedlock.spring.annotation.ScheduledLockConfiguration}
 * which intercepts Spring's {@code @Scheduled} methods and applies distributed locks
 * to those annotated with {@code @SchedulerLock}.
 */
@AutoConfiguration
@ConditionalOnClass({RedisConnectionFactory.class, LockProvider.class})
@ConditionalOnProperty(prefix = "acme.scheduler-lock", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableSchedulerLock(
        defaultLockAtMostFor = "${acme.scheduler-lock.default-lock-at-most:PT10M}",
        defaultLockAtLeastFor = "${acme.scheduler-lock.default-lock-at-least:PT5S}")
@EnableConfigurationProperties(SchedulerLockProperties.class)
public class SchedulerLockAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLockAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory,
                                     SchedulerLockProperties properties,
                                     @Value("${spring.application.name:unknown}") String appName) {
        log.info("Configuring Redis-backed ShedLock (env={}, keyPrefix={}, defaultLockAtMost={}, defaultLockAtLeast={})",
                appName, properties.getKeyPrefix(), properties.getDefaultLockAtMost(), properties.getDefaultLockAtLeast());

        return new RedisLockProvider(connectionFactory, appName, properties.getKeyPrefix());
    }
}
