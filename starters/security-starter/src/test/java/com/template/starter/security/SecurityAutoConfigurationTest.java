package com.template.starter.security;

import com.template.starter.security.filter.JwtAuthenticationFilter;
import com.template.starter.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAutoConfigurationTest {

    private static final String SECRET_64 = "a]5Dk{P9qR#mL2nX7vJ!wF0zT4bY8cU3eH6gA1iK&oS*dN=fQ+rMxW@pC(jEyV$Z"; // 64 chars

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
            ))
            .withPropertyValues("acme.security.jwt.secret=" + SECRET_64);

    @Test
    void autoConfiguration_registersCoreBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtService.class);
            assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
            assertThat(context).hasSingleBean(PasswordEncoder.class);
        });
    }

    @Test
    void autoConfiguration_noRoleHierarchyByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(RoleHierarchy.class);
        });
    }

    @Test
    void autoConfiguration_roleHierarchyCreatedWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "acme.security.jwt.role-hierarchy[0]=ROLE_ADMIN > ROLE_MODERATOR",
                        "acme.security.jwt.role-hierarchy[1]=ROLE_MODERATOR > ROLE_USER"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RoleHierarchy.class);
                });
    }

    @Test
    void autoConfiguration_shortSecretFailsFast() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .withPropertyValues("acme.security.jwt.secret=tooshort")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("at least 64 characters");
                });
    }

    @Test
    void autoConfiguration_missingSecretFailsFast() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }
}
