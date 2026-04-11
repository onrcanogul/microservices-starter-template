package com.template.starter.logging;

import com.template.starter.logging.filter.MdcFilter;
import com.template.starter.logging.property.LoggingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

    @Test
    void autoConfiguration_registersFilterByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).hasSingleBean(LoggingProperties.class);

            @SuppressWarnings("unchecked")
            FilterRegistrationBean<MdcFilter> registration =
                    context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(MdcFilter.class);
        });
    }

    @Test
    void autoConfiguration_disabledByProperty() {
        contextRunner
                .withPropertyValues("acme.logging.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void autoConfiguration_mdcFilterDisabledByProperty() {
        contextRunner
                .withPropertyValues("acme.logging.mdc.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                    // Properties bean should still be present
                    assertThat(context).hasSingleBean(LoggingProperties.class);
                });
    }

    @Test
    void autoConfiguration_respectsConditionalOnMissingBean() {
        contextRunner
                .withBean(MdcFilter.class, () -> new MdcFilter("custom", "custom", "custom"))
                .run(context -> {
                    // Custom bean should prevent auto-configured one
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void properties_defaultValues() {
        contextRunner.run(context -> {
            LoggingProperties props = context.getBean(LoggingProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getMdc().isEnabled()).isTrue();
            assertThat(props.getMdc().getUserIdHeader()).isEqualTo("X-User-Id");
            assertThat(props.getMdc().getCorrelationIdHeader()).isEqualTo("X-Correlation-Id");
        });
    }
}
