package com.template.starter.idempotency.filter;

import com.template.starter.idempotency.Idempotent;
import com.template.starter.idempotency.model.CachedResponse;
import com.template.starter.idempotency.property.IdempotencyProperties;
import com.template.starter.idempotency.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Servlet filter that enforces HTTP idempotency on endpoints annotated with {@link Idempotent}.
 * <p>
 * Flow:
 * <ol>
 *   <li>Resolve the handler method for the request</li>
 *   <li>If handler has {@code @Idempotent}, require the idempotency key header</li>
 *   <li>Check Redis for a cached response → replay if found</li>
 *   <li>Acquire a distributed lock → 409 if another request holds it</li>
 *   <li>Execute the handler, capture the response</li>
 *   <li>Cache 2xx responses in Redis with TTL; release the lock</li>
 * </ol>
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private final IdempotencyService idempotencyService;
    private final IdempotencyProperties properties;
    private final RequestMappingHandlerMapping handlerMapping;

    public IdempotencyFilter(IdempotencyService idempotencyService,
                             IdempotencyProperties properties,
                             RequestMappingHandlerMapping handlerMapping) {
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.handlerMapping = handlerMapping;
    }

    private static final java.util.Set<String> SAFE_METHODS = java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Short-circuit for naturally idempotent HTTP methods
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Idempotent annotation = resolveAnnotation(request);
        if (annotation == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(properties.getHeaderName());
        if (rawKey == null || rawKey.isBlank()) {
            writeMissingKeyError(response);
            return;
        }

        // Scope key by HTTP method + URI to prevent cross-endpoint cache collisions
        String scopedKey = request.getMethod() + ":" + request.getRequestURI() + ":" + rawKey;

        // Check for cached response
        Optional<CachedResponse> cached = idempotencyService.get(scopedKey);
        if (cached.isPresent()) {
            log.debug("Replaying cached response for idempotency key [{}]", rawKey);
            writeCachedResponse(response, cached.get());
            return;
        }

        // Acquire lock to prevent concurrent duplicate processing
        if (!idempotencyService.tryLock(scopedKey)) {
            log.debug("Concurrent request detected for idempotency key [{}]", rawKey);
            writeConflictError(response);
            return;
        }

        // Re-check cache after acquiring lock (double-check pattern)
        cached = idempotencyService.get(scopedKey);
        if (cached.isPresent()) {
            idempotencyService.unlock(scopedKey);
            log.debug("Replaying cached response (post-lock) for idempotency key [{}]", rawKey);
            writeCachedResponse(response, cached.get());
            return;
        }

        // Execute the handler with response wrapping
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
            cacheResponseIfSuccessful(scopedKey, wrappedResponse, annotation);
            wrappedResponse.copyBodyToResponse();
        } finally {
            idempotencyService.unlock(scopedKey);
        }
    }

    /**
     * Resolves the {@link Idempotent} annotation from the handler method mapped to this request.
     * Returns {@code null} if the request does not map to an {@code @Idempotent} handler.
     */
    private Idempotent resolveAnnotation(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
                return handlerMethod.getMethodAnnotation(Idempotent.class);
            }
        } catch (Exception e) {
            log.debug("Could not resolve handler for idempotency check", e);
        }
        return null;
    }

    private void cacheResponseIfSuccessful(String idempotencyKey,
                                           ContentCachingResponseWrapper wrappedResponse,
                                           Idempotent annotation) {
        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            String contentType = wrappedResponse.getContentType();
            String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            Map<String, String> headers = captureHeaders(wrappedResponse);
            CachedResponse cachedResponse = new CachedResponse(status, contentType, body, headers);
            idempotencyService.store(idempotencyKey, cachedResponse, annotation.ttlSeconds());
        } else {
            log.debug("Non-2xx response (status={}) not cached for idempotency key [{}]", status, idempotencyKey);
        }
    }

    private Map<String, String> captureHeaders(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : response.getHeaderNames()) {
            // Skip content-type (stored separately) and transfer-encoding (chunked artifact)
            if ("Content-Type".equalsIgnoreCase(headerName)
                    || "Transfer-Encoding".equalsIgnoreCase(headerName)
                    || "Content-Length".equalsIgnoreCase(headerName)) {
                continue;
            }
            headers.put(headerName, response.getHeader(headerName));
        }
        return headers;
    }

    private void writeCachedResponse(HttpServletResponse response, CachedResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        if (cached.headers() != null) {
            cached.headers().forEach(response::setHeader);
        }
        response.getWriter().write(cached.body());
        response.getWriter().flush();
    }

    private void writeMissingKeyError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"missing_idempotency_key\","
                        + "\"message\":\"" + properties.getHeaderName() + " header is required for this endpoint\"}}");
        response.getWriter().flush();
    }

    private void writeConflictError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"concurrent_request\","
                        + "\"message\":\"A request with this idempotency key is already being processed\"}}");
        response.getWriter().flush();
    }
}
