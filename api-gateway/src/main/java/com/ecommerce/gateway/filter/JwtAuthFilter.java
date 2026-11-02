package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;

    // Public paths that bypass authentication unconditionally for any HTTP method
    private static final List<String> UNCONDITIONAL_PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/google",
            "/v3/api-docs",
            "/swagger-ui",
            "/actuator"
    );

    // Catalog endpoints that bypass authentication ONLY for HTTP GET requests
    private static final List<String> GET_ONLY_PUBLIC_PATHS = List.of(
            "/api/products",
            "/api/categories"
    );

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private boolean isPublicRequest(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return true;
        }

        String path = request.getURI().getPath();

        if (UNCONDITIONAL_PUBLIC_PATHS.stream().anyMatch(base -> isPathOrSubpath(path, base))) {
            return true;
        }

        if (HttpMethod.GET.equals(request.getMethod()) &&
                GET_ONLY_PUBLIC_PATHS.stream().anyMatch(base -> isPathOrSubpath(path, base))) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a request path matches the base path exactly or is a direct subpath
     * (e.g. /api/products or /api/products/123, but NOT /api/products-internal).
     */
    private static boolean isPathOrSubpath(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Check if path and method constitute a public endpoint
        boolean isPublic = isPublicRequest(request);
        if (isPublic) {
            // Strip any incoming client-supplied spoofed identity headers on public endpoints
            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.remove("X-User-Id");
                        httpHeaders.remove("X-User-Email");
                        httpHeaders.remove("X-User-Roles");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        // Check Authorization header for protected routes
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Unauthorized access attempt to {}: Missing Bearer token", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            log.warn("Unauthorized access attempt to {}: Invalid or expired JWT", path);
            return onError(exchange, "Expired or invalid JWT token", HttpStatus.UNAUTHORIZED);
        }

        Claims claims = jwtUtil.extractClaims(token);
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        log.info("JWT validated for path={}. Forwarding headers X-User-Id={}, email={}, role={}",
                path, userId, email, role);

        // Inject identity headers for downstream microservices, ensuring any client-supplied spoofed headers are stripped and overwritten
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove("X-User-Id");
                    httpHeaders.remove("X-User-Email");
                    httpHeaders.remove("X-User-Roles");
                    httpHeaders.set("X-User-Id", userId != null ? userId : "");
                    httpHeaders.set("X-User-Email", email != null ? email : "");
                    httpHeaders.set("X-User-Roles", role != null ? role : "");
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getURI().getPath();
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"success\":false}",
                Instant.now(), status.value(), status.getReasonPhrase(), message, path
        );

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // High precedence before routing
    }
}
