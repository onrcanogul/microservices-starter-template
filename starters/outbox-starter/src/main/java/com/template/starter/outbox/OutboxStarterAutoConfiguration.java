package com.template.starter.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(prefix = "outbox.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxStarterAutoConfiguration {
}
