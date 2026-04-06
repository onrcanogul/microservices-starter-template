package com.template.starter.inbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@ComponentScan(basePackageClasses = InboxStarterMarker.class)
@EnableJpaRepositories(basePackageClasses = InboxStarterMarker.class)
@EntityScan(basePackageClasses = InboxStarterMarker.class)
@ConditionalOnProperty(prefix = "acme.inbox.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InboxAutoConfiguration {

}
