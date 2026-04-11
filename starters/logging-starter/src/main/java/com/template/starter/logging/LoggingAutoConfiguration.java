package com.template.starter.logging;

import com.template.starter.logging.filter.MdcFilter;
import com.template.starter.logging.property.LoggingProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(prefix = "acme.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggingAutoConfiguration {

    /**
     * MDC propagation filter — extracts user context and correlation ID from request headers
     * and populates SLF4J MDC for structured log enrichment.
     *
     * <p>Registered with highest precedence so MDC is available to all downstream filters and handlers.</p>
     */
    @Bean
    @ConditionalOnMissingBean(MdcFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "acme.logging.mdc", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration(LoggingProperties properties) {
        LoggingProperties.Mdc mdc = properties.getMdc();
        MdcFilter filter = new MdcFilter(
                mdc.getUserIdHeader(),
                mdc.getUserEmailHeader(),
                mdc.getCorrelationIdHeader()
        );

        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // Before security filters
        registration.addUrlPatterns("/*");
        return registration;
    }
}
