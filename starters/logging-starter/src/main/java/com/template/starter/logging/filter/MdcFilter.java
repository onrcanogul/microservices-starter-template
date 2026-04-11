package com.template.starter.logging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates MDC with request context for structured logging.
 *
 * <p>Extracts user identity headers (set by the API gateway's JWT filter) and
 * correlation IDs from incoming requests. If no correlation ID header is present,
 * a new UUID is generated to ensure every request is traceable.</p>
 *
 * <p>MDC keys populated:</p>
 * <ul>
 *   <li>{@code userId} — from X-User-Id header</li>
 *   <li>{@code userEmail} — from X-User-Email header</li>
 *   <li>{@code correlationId} — from X-Correlation-Id header (or generated UUID)</li>
 *   <li>{@code requestMethod} — HTTP method (GET, POST, etc.)</li>
 *   <li>{@code requestUri} — request URI path</li>
 * </ul>
 */
public class MdcFilter extends OncePerRequestFilter {

    public static final String MDC_USER_ID = "userId";
    public static final String MDC_USER_EMAIL = "userEmail";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_REQUEST_METHOD = "requestMethod";
    public static final String MDC_REQUEST_URI = "requestUri";

    private final String userIdHeader;
    private final String userEmailHeader;
    private final String correlationIdHeader;

    public MdcFilter(String userIdHeader, String userEmailHeader, String correlationIdHeader) {
        this.userIdHeader = userIdHeader;
        this.userEmailHeader = userEmailHeader;
        this.correlationIdHeader = correlationIdHeader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            populateMdc(request);
            // Propagate correlation ID to response for client-side tracing
            String correlationId = MDC.get(MDC_CORRELATION_ID);
            if (correlationId != null) {
                response.setHeader(correlationIdHeader, correlationId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_USER_EMAIL);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_REQUEST_METHOD);
            MDC.remove(MDC_REQUEST_URI);
        }
    }

    private void populateMdc(HttpServletRequest request) {
        String userId = request.getHeader(userIdHeader);
        if (userId != null && !userId.isBlank()) {
            MDC.put(MDC_USER_ID, userId);
        }

        String userEmail = request.getHeader(userEmailHeader);
        if (userEmail != null && !userEmail.isBlank()) {
            MDC.put(MDC_USER_EMAIL, userEmail);
        }

        String correlationId = request.getHeader(correlationIdHeader);
        MDC.put(MDC_CORRELATION_ID,
                (correlationId != null && !correlationId.isBlank())
                        ? correlationId
                        : UUID.randomUUID().toString());

        MDC.put(MDC_REQUEST_METHOD, request.getMethod());
        MDC.put(MDC_REQUEST_URI, request.getRequestURI());
    }
}
