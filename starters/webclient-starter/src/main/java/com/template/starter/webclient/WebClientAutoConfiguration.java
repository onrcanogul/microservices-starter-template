package com.template.starter.webclient;

import com.template.starter.webclient.property.WebClientProperties;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.http.HttpClient;

/**
 * Auto-configuration providing pre-configured {@link RestClient.Builder} and
 * {@link WebClient.Builder} with:
 * <ul>
 *   <li><b>Load balancing</b> — via Spring Cloud LoadBalancer + Eureka</li>
 *   <li><b>Trace propagation</b> — via Micrometer {@link ObservationRegistry}</li>
 *   <li><b>Header propagation</b> — forwards {@code X-User-Id} and {@code X-User-Email}
 *       from the incoming request to outbound calls</li>
 *   <li><b>Connection pooling</b> — via JDK 21 {@link HttpClient} built-in pool</li>
 *   <li><b>Timeouts</b> — configurable connection and read timeouts</li>
 * </ul>
 *
 * <p><strong>Note:</strong> Header propagation uses {@code RequestContextHolder} (ThreadLocal)
 * and will silently skip propagation on non-servlet threads ({@code @Async},
 * {@code CompletableFuture.supplyAsync}, virtual threads without inheritable attributes).
 * A debug-level log message is emitted when this occurs.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "acme.webclient", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebClientProperties.class)
public class WebClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebClientAutoConfiguration.class);

    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_EMAIL = "X-User-Email";

    /**
     * Load-balanced {@link RestClient.Builder} for synchronous service-to-service calls.
     * <p>
     * Uses JDK 21's {@link HttpClient} for built-in connection pooling, configurable
     * timeouts, and HTTP/2 support.
     * <p>
     * Usage:
     * <pre>{@code
     * @Autowired RestClient.Builder restClientBuilder;
     *
     * RestClient client = restClientBuilder
     *     .baseUrl("http://payment-service")
     *     .build();
     *
     * PaymentResponse resp = client.get()
     *     .uri("/api/payments/{id}", paymentId)
     *     .retrieve()
     *     .body(PaymentResponse.class);
     * }</pre>
     */
    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(name = "loadBalancedRestClientBuilder")
    public RestClient.Builder loadBalancedRestClientBuilder(
            WebClientProperties props,
            ObjectProvider<ObservationRegistry> observationRegistryProvider) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory);

        // Trace propagation via ObservationRegistry (auto-instruments RestClient)
        ObservationRegistry obsRegistry = observationRegistryProvider.getIfAvailable();
        if (obsRegistry != null) {
            builder.observationRegistry(obsRegistry);
            log.info("webclient-starter: RestClient trace propagation enabled via ObservationRegistry");
        }

        // Header propagation: forward X-User-Id / X-User-Email from incoming request
        if (props.getHeaderPropagation().isEnabled()) {
            builder.requestInterceptor(userHeaderPropagationInterceptor());
            log.info("webclient-starter: X-User-Id / X-User-Email header propagation enabled");
        }

        log.info("webclient-starter: RestClient.Builder configured (connectTimeout={}, readTimeout={}, factory=JdkClientHttpRequestFactory)",
                props.getConnectTimeout(), props.getReadTimeout());

        return builder;
    }

    /**
     * Load-balanced {@link WebClient.Builder} for reactive service-to-service calls.
     * <p>
     * Configured with observation registry for trace propagation and
     * header forwarding filter. Most servlet-based services should prefer
     * the {@link RestClient.Builder} instead.
     */
    @Bean
    @LoadBalanced
    @ConditionalOnClass(WebClient.class)
    @ConditionalOnMissingBean(name = "loadBalancedWebClientBuilder")
    public WebClient.Builder loadBalancedWebClientBuilder(
            WebClientProperties props,
            ObjectProvider<ObservationRegistry> observationRegistryProvider) {

        WebClient.Builder builder = WebClient.builder();

        // Trace propagation
        ObservationRegistry obsRegistry = observationRegistryProvider.getIfAvailable();
        if (obsRegistry != null) {
            builder.observationRegistry(obsRegistry);
        }

        // Header propagation for reactive calls
        if (props.getHeaderPropagation().isEnabled()) {
            builder.filter(userHeaderPropagationFilter());
        }

        log.info("webclient-starter: WebClient.Builder configured (load-balanced)");
        return builder;
    }

    /**
     * Interceptor that copies {@code X-User-Id} and {@code X-User-Email} from
     * the current servlet request to outbound RestClient calls.
     * <p>
     * <strong>Limitation:</strong> Uses {@code RequestContextHolder} (ThreadLocal).
     * Headers are not propagated on {@code @Async} or {@code CompletableFuture} threads.
     */
    private ClientHttpRequestInterceptor userHeaderPropagationInterceptor() {
        return (request, body, execution) -> {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                var incomingRequest = servletAttrs.getRequest();
                String userId = incomingRequest.getHeader(X_USER_ID);
                String userEmail = incomingRequest.getHeader(X_USER_EMAIL);

                if (userId != null) {
                    request.getHeaders().set(X_USER_ID, userId);
                }
                if (userEmail != null) {
                    request.getHeaders().set(X_USER_EMAIL, userEmail);
                }
            } else {
                log.debug("webclient-starter: RequestContextHolder has no servlet attributes — " +
                        "X-User-Id/X-User-Email headers will not be propagated. " +
                        "This is expected on @Async or CompletableFuture threads.");
            }
            return execution.execute(request, body);
        };
    }

    /**
     * Reactive filter that copies {@code X-User-Id} and {@code X-User-Email} from
     * the current servlet request to outbound WebClient calls.
     */
    private ExchangeFilterFunction userHeaderPropagationFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                var incomingRequest = servletAttrs.getRequest();
                String userId = incomingRequest.getHeader(X_USER_ID);
                String userEmail = incomingRequest.getHeader(X_USER_EMAIL);

                var mutatedRequest = org.springframework.web.reactive.function.client.ClientRequest.from(clientRequest);
                if (userId != null) {
                    mutatedRequest.header(X_USER_ID, userId);
                }
                if (userEmail != null) {
                    mutatedRequest.header(X_USER_EMAIL, userEmail);
                }
                return Mono.just(mutatedRequest.build());
            }
            log.debug("webclient-starter: No servlet attributes for WebClient header propagation");
            return Mono.just(clientRequest);
        });
    }
}
