package com.template.starter.logging.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter("X-User-Id", "X-User-Email", "X-Correlation-Id");

    @Test
    void doFilter_populatesMdcFromHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "user-123");
        request.addHeader("X-User-Email", "user@test.com");
        request.addHeader("X-Correlation-Id", "corr-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(MDC.get("userId")).isEqualTo("user-123");
            assertThat(MDC.get("userEmail")).isEqualTo("user@test.com");
            assertThat(MDC.get("correlationId")).isEqualTo("corr-abc");
            assertThat(MDC.get("requestMethod")).isEqualTo("GET");
            assertThat(MDC.get("requestUri")).isEqualTo("/api/orders");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);

        // MDC should be cleared after filter
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("userEmail")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("requestMethod")).isNull();
        assertThat(MDC.get("requestUri")).isNull();
    }

    @Test
    void doFilter_generatesCorrelationIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            String correlationId = MDC.get("correlationId");
            assertThat(correlationId).isNotNull().isNotBlank();
            // Should be a valid UUID format
            assertThat(correlationId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void doFilter_skipsMdcForBlankHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "  ");
        request.addHeader("X-User-Email", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(MDC.get("userId")).isNull();
            assertThat(MDC.get("userEmail")).isNull();
            // Correlation ID is always generated
            assertThat(MDC.get("correlationId")).isNotNull();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void doFilter_propagatesCorrelationIdToResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Correlation-Id", "corr-xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("corr-xyz");
    }

    @Test
    void doFilter_clearsMdcEvenOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "user-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("test error")).when(chain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (RuntimeException ignored) {}

        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }
}
