package com.template.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    @Value("jwt-secret")
    private String SECRET_KEY;
    private static final List<String> openPaths = List.of(
            "/auth/login", "/auth/register", "/auth/refresh"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //todo (defense in depth)
        String path = exchange.getRequest().getPath().toString();
        if (openPaths.stream().anyMatch(path::startsWith))
            return chain.filter(exchange);

        ServerHttpRequest req = exchange.getRequest();
        if (!req.getHeaders().containsKey(HttpHeaders.AUTHORIZATION))
            return onError(exchange, "Authorization header missing", HttpStatus.UNAUTHORIZED);
        String authHeader = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return onError(exchange, "Invalid Authorization header", HttpStatus.UNAUTHORIZED);

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
            exchange.getRequest().mutate()
                    .header("X-User-Name", claims.getSubject())
                    .build();

        } catch (Exception e) {
            return onError(exchange, "Invalid token: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        DataBuffer buffer = response.bufferFactory().wrap(err.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // first
    }
}

