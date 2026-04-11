package com.template.starter.idempotency.filter;

import com.template.starter.idempotency.Idempotent;
import com.template.starter.idempotency.model.CachedResponse;
import com.template.starter.idempotency.property.IdempotencyProperties;
import com.template.starter.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    private static final String SCOPED_KEY_ORDERS = "POST:/api/orders:";

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    private IdempotencyProperties properties;
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        properties = new IdempotencyProperties();
        filter = new IdempotencyFilter(idempotencyService, properties, handlerMapping);
    }

    @Test
    void doFilter_safeMethod_passesWithoutHandlerResolution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(idempotencyService);
        verifyNoInteractions(handlerMapping);
    }

    @Test
    void doFilter_nonAnnotatedPostEndpoint_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/other");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(handlerMapping.getHandler(request)).thenReturn(null);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void doFilter_missingHeader_returns400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("missing_idempotency_key");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_blankHeader_returns400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("missing_idempotency_key");
    }

    @Test
    void doFilter_cachedResponse_replaysWithoutExecution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(SCOPED_KEY_ORDERS + "key-123"))
                .thenReturn(Optional.of(new CachedResponse(201, "application/json", "{\"data\":\"cached\"}", Map.of("Location", "/api/orders/1"))));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"data\":\"cached\"}");
        assertThat(response.getHeader("Location")).isEqualTo("/api/orders/1");
        assertThat(chain.getRequest()).isNull();
        verify(idempotencyService, never()).tryLock(any());
    }

    @Test
    void doFilter_concurrentRequest_returns409() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(SCOPED_KEY_ORDERS + "key-456")).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(SCOPED_KEY_ORDERS + "key-456")).thenReturn(false);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("concurrent_request");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_newRequest_executesAndCaches() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-789");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(SCOPED_KEY_ORDERS + "key-789")).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(SCOPED_KEY_ORDERS + "key-789")).thenReturn(true);

        FilterChain chain = (req, res) -> {
            res.setContentType("application/json");
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
            res.getWriter().write("{\"data\":\"new\"}");
        };
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(idempotencyService).store(eq(SCOPED_KEY_ORDERS + "key-789"), any(CachedResponse.class), eq(-1L));
        verify(idempotencyService).unlock(SCOPED_KEY_ORDERS + "key-789");
    }

    @Test
    void doFilter_errorResponse_notCached() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-err");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(SCOPED_KEY_ORDERS + "key-err")).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(SCOPED_KEY_ORDERS + "key-err")).thenReturn(true);

        FilterChain chain = (req, res) -> {
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(500);
            res.getWriter().write("error");
        };
        filter.doFilterInternal(request, response, chain);

        verify(idempotencyService, never()).store(any(), any(), anyLong());
        verify(idempotencyService).unlock(SCOPED_KEY_ORDERS + "key-err");
    }

    @Test
    void doFilter_postLockCacheHit_replaysAndReleasesLock() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-race");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(SCOPED_KEY_ORDERS + "key-race"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new CachedResponse(201, "application/json", "{\"cached\":true}", Map.of())));
        when(idempotencyService.tryLock(SCOPED_KEY_ORDERS + "key-race")).thenReturn(true);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"cached\":true}");
        assertThat(chain.getRequest()).isNull();
        verify(idempotencyService).unlock(SCOPED_KEY_ORDERS + "key-race");
        verify(idempotencyService, never()).store(any(), any(), anyLong());
    }

    @Test
    void doFilter_handlerThrows_unlocksAndPropagates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-throw");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = handlerMethod("idempotentEndpoint");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(SCOPED_KEY_ORDERS + "key-throw")).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(SCOPED_KEY_ORDERS + "key-throw")).thenReturn(true);

        FilterChain chain = (req, res) -> { throw new RuntimeException("boom"); };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(idempotencyService).unlock(SCOPED_KEY_ORDERS + "key-throw");
        verify(idempotencyService, never()).store(any(), any(), anyLong());
    }

    @Test
    void doFilter_customTtl_passedToStore() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment");
        request.addHeader("Idempotency-Key", "key-ttl");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String scopedKey = "POST:/api/payment:key-ttl";

        HandlerMethod handlerMethod = handlerMethod("idempotentWithCustomTtl");
        when(handlerMapping.getHandler(request)).thenReturn(new HandlerExecutionChain(handlerMethod));
        when(idempotencyService.get(scopedKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(scopedKey)).thenReturn(true);

        FilterChain chain = (req, res) -> {
            res.setContentType("application/json");
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
            res.getWriter().write("{}");
        };
        filter.doFilterInternal(request, response, chain);

        verify(idempotencyService).store(eq(scopedKey), any(), eq(3600L));
    }

    // --- Helper: build HandlerMethod from test controller ---

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), methodName);
    }

    @RestController
    static class TestController {

        @GetMapping("/api/orders")
        public ResponseEntity<String> nonIdempotent() {
            return ResponseEntity.ok("ok");
        }

        @PostMapping("/api/orders")
        @Idempotent
        public ResponseEntity<String> idempotentEndpoint() {
            return ResponseEntity.ok("created");
        }

        @PostMapping("/api/payment")
        @Idempotent(ttlSeconds = 3600)
        public ResponseEntity<String> idempotentWithCustomTtl() {
            return ResponseEntity.ok("charged");
        }
    }
}
