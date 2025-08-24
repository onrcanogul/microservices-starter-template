package com.template.web;

import com.template.web.handler.GlobalExceptionHandler;
import com.template.web.property.WebErrorProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-config entry point for commons-web.
 * Registers global exception handling with sensible defaults.
 */
@AutoConfiguration
@EnableConfigurationProperties(WebErrorProperties.class)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler(WebErrorProperties props) {
        return new GlobalExceptionHandler(props);
    }
}
