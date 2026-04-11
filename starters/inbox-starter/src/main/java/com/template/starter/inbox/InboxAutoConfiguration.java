package com.template.starter.inbox;

import com.template.messaging.event.version.EventUpcastChain;
import com.template.messaging.event.version.EventUpcaster;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@AutoConfiguration
@EnableScheduling
@ComponentScan(basePackageClasses = InboxStarterMarker.class)
@EnableJpaRepositories(basePackageClasses = InboxStarterMarker.class)
@EntityScan(basePackageClasses = InboxStarterMarker.class)
@ConditionalOnProperty(prefix = "acme.inbox.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventUpcastChain eventUpcastChain(ObjectProvider<List<EventUpcaster>> upcastersProvider) {
        List<EventUpcaster> upcasters = upcastersProvider.getIfAvailable(List::of);
        return new EventUpcastChain(upcasters);
    }
}
