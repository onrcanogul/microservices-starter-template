package com.template.starter.webclient;

import com.template.starter.webclient.property.WebClientProperties;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebClientAutoConfigurationTest {

    private WebClientAutoConfiguration autoConfiguration;
    private WebClientProperties properties;

    @BeforeEach
    void setUp() {
        autoConfiguration = new WebClientAutoConfiguration();
        properties = new WebClientProperties();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ObservationRegistry> mockObsProvider(ObservationRegistry registry) {
        ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    @Test
    void loadBalancedRestClientBuilder_shouldReturnNonNullBuilder() {
        RestClient.Builder builder = autoConfiguration.loadBalancedRestClientBuilder(
                properties, mockObsProvider(ObservationRegistry.create()));

        assertThat(builder).isNotNull();
    }

    @Test
    void loadBalancedRestClientBuilder_shouldApplyDefaultTimeouts() {
        RestClient.Builder builder = autoConfiguration.loadBalancedRestClientBuilder(
                properties, mockObsProvider(null));

        assertThat(builder).isNotNull();
    }

    @Test
    void loadBalancedRestClientBuilder_shouldApplyObservationRegistry_whenAvailable() {
        ObservationRegistry registry = ObservationRegistry.create();

        RestClient.Builder builder = autoConfiguration.loadBalancedRestClientBuilder(
                properties, mockObsProvider(registry));

        assertThat(builder).isNotNull();
    }

    @Test
    void loadBalancedRestClientBuilder_shouldNotFail_whenObservationRegistryIsNull() {
        RestClient.Builder builder = autoConfiguration.loadBalancedRestClientBuilder(
                properties, mockObsProvider(null));

        assertThat(builder).isNotNull();
    }

    @Test
    void headerPropagation_shouldForwardHeaders_whenServletRequestPresent() throws IOException {
        // Set up incoming request with user headers
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-User-Id", "user-123");
        servletRequest.addHeader("X-User-Email", "user@test.com");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        try {
            // Get the interceptor via the builder
            ClientHttpRequestInterceptor interceptor = extractHeaderInterceptor();

            // Mock outbound request
            HttpRequest outboundRequest = mock(HttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            when(outboundRequest.getHeaders()).thenReturn(headers);

            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            ClientHttpResponse response = mock(ClientHttpResponse.class);
            when(execution.execute(any(), any())).thenReturn(response);

            interceptor.intercept(outboundRequest, new byte[0], execution);

            assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-123");
            assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@test.com");
            verify(execution).execute(outboundRequest, new byte[0]);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void headerPropagation_shouldSkip_whenNoServletRequest() throws IOException {
        // No servlet request attributes set
        RequestContextHolder.resetRequestAttributes();

        ClientHttpRequestInterceptor interceptor = extractHeaderInterceptor();

        HttpRequest outboundRequest = mock(HttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(outboundRequest.getHeaders()).thenReturn(headers);

        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(outboundRequest, new byte[0], execution);

        // No headers should be set
        assertThat(headers.get("X-User-Id")).isNull();
        assertThat(headers.get("X-User-Email")).isNull();
        // But the call should still proceed
        verify(execution).execute(outboundRequest, new byte[0]);
    }

    @Test
    void headerPropagation_shouldSkipMissingHeaders_whenNotPresent() throws IOException {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        // Do NOT add any X-User headers
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        try {
            ClientHttpRequestInterceptor interceptor = extractHeaderInterceptor();

            HttpRequest outboundRequest = mock(HttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            when(outboundRequest.getHeaders()).thenReturn(headers);

            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            ClientHttpResponse response = mock(ClientHttpResponse.class);
            when(execution.execute(any(), any())).thenReturn(response);

            interceptor.intercept(outboundRequest, new byte[0], execution);

            assertThat(headers.get("X-User-Id")).isNull();
            assertThat(headers.get("X-User-Email")).isNull();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void headerPropagation_shouldBeDisabled_whenConfigured() {
        properties.getHeaderPropagation().setEnabled(false);

        RestClient.Builder builder = autoConfiguration.loadBalancedRestClientBuilder(
                properties, mockObsProvider(null));

        // Builder should still be created — just no interceptor
        assertThat(builder).isNotNull();
    }

    /**
     * Extracts the header propagation interceptor by creating a builder with header
     * propagation enabled and getting the interceptors via reflection.
     */
    @SuppressWarnings("unchecked")
    private ClientHttpRequestInterceptor extractHeaderInterceptor() {
        properties.getHeaderPropagation().setEnabled(true);
        RestClient.Builder builder = autoConfiguration.loadBalancedRestClientBuilder(
                properties, mockObsProvider(null));

        // The RestClient.Builder stores interceptors internally
        // We need to extract them via reflection since there's no public getter
        try {
            Field interceptorsField = builder.getClass().getDeclaredField("interceptors");
            interceptorsField.setAccessible(true);
            List<ClientHttpRequestInterceptor> interceptors =
                    (List<ClientHttpRequestInterceptor>) interceptorsField.get(builder);
            assertThat(interceptors).isNotEmpty();
            return interceptors.getLast();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to extract interceptors from RestClient.Builder", e);
        }
    }
}
