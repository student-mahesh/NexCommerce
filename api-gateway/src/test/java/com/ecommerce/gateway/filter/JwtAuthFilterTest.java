package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
        filter = new JwtAuthFilter(jwtUtil);
        lenient().when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String generateToken(String subject, String email, String role, long deltaMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.plusMillis(deltaMs)))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Should allow public login endpoint without token")
    void shouldBypassFilterForPublicLogin() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should allow public product catalog endpoint without token")
    void shouldBypassFilterForPublicProducts() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should allow public single product endpoint without token")
    void shouldBypassFilterForPublicSingleProduct() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should allow public category listing endpoint without token")
    void shouldBypassFilterForPublicCategories() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/categories").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should allow public category subpath endpoint without token")
    void shouldBypassFilterForPublicCategorySubpath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/categories/5").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should allow CORS preflight OPTIONS request without token")
    void shouldBypassFilterForCorsPreflightOptions() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/api/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should allow public actuator health check endpoint without token")
    void shouldBypassFilterForActuatorHealth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should reject unintended similar path prefix /api/products-secret without token")
    void shouldRejectUnintendedPrefixProductsSecretWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products-secret").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject unintended similar path prefix /api/categories-private without token")
    void shouldRejectUnintendedPrefixCategoriesPrivateWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/categories-private").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject product PATCH mutation without Authorization token")
    void shouldRejectPatchProductsWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.patch("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject product creation (POST) without Authorization token")
    void shouldRejectPostProductsWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject product update (PUT) without Authorization token")
    void shouldRejectPutProductsWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.put("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject product deletion (DELETE) without Authorization token")
    void shouldRejectDeleteProductsWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should allow admin to create product (POST) with valid JWT and inject role")
    void shouldAllowAdminPostProductsWithToken() {
        String adminToken = generateToken("2", "admin@test.com", "ROLE_ADMIN", 60000);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("2");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("admin@test.com");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should reject protected order endpoint when Authorization header is missing")
    void shouldRejectProtectedEndpointWithoutAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject protected endpoint when token is invalid or tampered")
    void shouldRejectProtectedEndpointWithInvalidToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.tampered.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject protected endpoint when token is expired")
    void shouldRejectProtectedEndpointWithExpiredToken() {
        String expiredToken = generateToken("1", "customer@test.com", "ROLE_CUSTOMER", -10000);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should validate customer JWT and inject identity headers downstream")
    void shouldInjectCustomerIdentityHeaders() {
        String token = generateToken("1", "customer@test.com", "ROLE_CUSTOMER", 60000);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("customer@test.com");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("Should validate admin JWT and inject admin identity headers downstream")
    void shouldInjectAdminIdentityHeaders() {
        String token = generateToken("2", "admin@test.com", "ROLE_ADMIN", 60000);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("2");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("admin@test.com");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should strip client-supplied spoofed identity headers on public endpoints")
    void shouldStripSpoofedIdentityHeadersOnPublicEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products")
                .header("X-User-Id", "999")
                .header("X-User-Roles", "ROLE_ADMIN")
                .header("X-User-Email", "attacker@hack.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles")).isNull();
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Email")).isNull();
    }

    @Test
    @DisplayName("Should overwrite client-supplied spoofed identity headers with valid JWT claims on protected endpoints")
    void shouldOverwriteSpoofedIdentityHeadersOnProtectedEndpoints() {
        String token = generateToken("1", "customer@test.com", "ROLE_CUSTOMER", 60000);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "999") // Attacker attempting to spoof ID 999
                .header("X-User-Roles", "ROLE_ADMIN") // Attacker attempting to escalate privilege
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        // Must strictly contain ONLY the authenticated JWT values
        assertThat(captured.getRequest().getHeaders().get("X-User-Id")).containsExactly("1");
        assertThat(captured.getRequest().getHeaders().get("X-User-Roles")).containsExactly("ROLE_CUSTOMER");
        assertThat(captured.getRequest().getHeaders().get("X-User-Email")).containsExactly("customer@test.com");
    }
}
