package com.template.starter.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.starter.idempotency.filter.IdempotencyFilter;
import com.template.starter.idempotency.property.IdempotencyProperties;
import com.template.starter.idempotency.service.IdempotencyService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Auto-configuration for HTTP-level idempotency.
 * <p>
 * Activated when {@code spring-data-redis} and {@code spring-webmvc} are on the classpath
 * and {@code acme.idempotency.enabled} is not explicitly set to {@code false}.
 * <p>
 * Registers:
 * <ul>
 *   <li>{@link IdempotencyService} — Redis operations for caching and locking</li>
 *   <li>{@link IdempotencyFilter} — servlet filter that intercepts {@code @Idempotent} endpoints</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, RequestMappingHandlerMapping.class})
@ConditionalOnProperty(prefix = "acme.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyService idempotencyService(StringRedisTemplate redisTemplate,
                                                 ObjectMapper objectMapper,
                                                 IdempotencyProperties properties) {
        return new IdempotencyService(redisTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyFilter idempotencyFilter(IdempotencyService idempotencyService,
                                               IdempotencyProperties properties,
                                               RequestMappingHandlerMapping handlerMapping) {
        return new IdempotencyFilter(idempotencyService, properties, handlerMapping);
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(0); // after Spring Security (order -100)
        registration.addUrlPatterns("/*");
        return registration;
    }
}
