package com.template.starter.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@ComponentScan(basePackageClasses = OutboxStarterMarker.class)
@EnableJpaRepositories(basePackageClasses = OutboxStarterMarker.class)
@EntityScan(basePackageClasses = OutboxStarterMarker.class)
@ConditionalOnProperty(prefix = "outbox.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxStarterAutoConfiguration {
}
