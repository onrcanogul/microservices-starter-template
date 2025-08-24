package com.template.observer;

import com.template.observer.property.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {
    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags(Environment env,
                                                      ObjectProvider<BuildProperties> buildPropsProvider,
                                                      ObservabilityProperties props) {
        return registry -> {
            String app = env.getProperty("spring.application.name", "app");
            String envName = env.getProperty("APP_ENV",
                    env.getProperty("spring.profiles.active", "dev"));
            String version = buildPropsProvider.getIfAvailable() != null
                    ? buildPropsProvider.getIfAvailable().getVersion()
                    : "local";

            registry.config().commonTags("app", app, "env", envName, "version", version);

            props.getCommonTags().forEach((k,v) -> registry.config().commonTags(k, v));
        };
    }

    /** Limit HTTP URI cardinality (prometheus won't explode) */
    @Bean
    MeterFilter httpUriCardinalityGuard(ObservabilityProperties props) {
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri",
                props.getMaxUniqueHttpUris(), MeterFilter.deny());
    }

    /** Add TraceId into header */
    @Bean
    @ConditionalOnClass(Tracer.class)
    @ConditionalOnProperty(prefix = "acme.obs", name = "add-trace-response-header", havingValue = "true", matchIfMissing = true)
    Filter traceIdResponseHeaderFilter(ObjectProvider<Tracer> tracerProvider) {
        Tracer tracer = tracerProvider.getIfAvailable();
        return (req, res, chain) -> {
            try {
                chain.doFilter(req, res);
            } finally {
                if (tracer != null && res instanceof HttpServletResponse) {
                    Span span = tracer.currentSpan();
                    if (span != null) {
                        ((HttpServletResponse) res).setHeader("Trace-Id", span.context().traceId());
                    }
                }
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "acme.obs.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    org.springframework.boot.actuate.autoconfigure.observation.ObservationProperties tracingPropsBridge(
            ObservabilityProperties props) {
        return new org.springframework.boot.actuate.autoconfigure.observation.ObservationProperties();
    }
}

