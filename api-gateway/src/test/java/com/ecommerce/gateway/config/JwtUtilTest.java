package com.ecommerce.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
    }

    private String createToken(String subject, String email, String role, long expirationDeltaMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.plusMillis(expirationDeltaMs)))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Should extract valid claims from a properly signed token")
    void shouldExtractClaimsFromValidToken() {
        String token = createToken("42", "shopper@test.com", "ROLE_CUSTOMER", 60000);

        assertThat(jwtUtil.validateToken(token)).isTrue();

        Claims claims = jwtUtil.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("shopper@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        // Token expired 5 seconds ago
        String expiredToken = createToken("42", "shopper@test.com", "ROLE_CUSTOMER", -5000);

        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered token with invalid signature")
    void shouldRejectTamperedToken() {
        String validToken = createToken("42", "shopper@test.com", "ROLE_CUSTOMER", 60000);
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        assertThat(jwtUtil.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject token signed with a different secret")
    void shouldRejectTokenSignedWithDifferentSecret() {
        String foreignSecret = "999E635266556A586E3272357538782F413F4428472B4B6250645367566B5999";
        SecretKey foreignKey = Keys.hmacShaKeyFor(foreignSecret.getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .subject("42")
                .claim("email", "fake@test.com")
                .claim("role", "ROLE_CUSTOMER")
                .signWith(foreignKey)
                .compact();

        assertThat(jwtUtil.validateToken(foreignToken)).isFalse();
    }
}

