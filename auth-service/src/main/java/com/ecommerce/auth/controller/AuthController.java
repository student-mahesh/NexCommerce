package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.*;
import com.ecommerce.auth.exception.UnauthorizedException;
import com.ecommerce.auth.security.JwtUtil;
import com.ecommerce.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, and Google OAuth2 APIs")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request for email={}", request.email());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and receive signed JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for email={}", request.email());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/google")
    @Operation(summary = "Exchange Google OAuth2 token for platform JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("Received Google OAuth2 login request");
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.ok("Google login successful", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get authenticated user profile from JWT Bearer token")
    public ResponseEntity<ApiResponse<AuthResponse>> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        log.info("Received getProfile request");
        Long userId = null;
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                userId = Long.parseLong(xUserId);
            } catch (NumberFormatException ignored) {}
        }

        if (userId == null) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedException("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                throw new UnauthorizedException("Expired or invalid JWT token");
            }

            Claims claims = jwtUtil.extractClaims(token);
            userId = Long.valueOf(claims.getSubject());
        }

        AuthResponse profile = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok("Profile retrieved successfully", profile));
    }

    @GetMapping("/verify-headers")
    @Operation(summary = "Verify identity headers forwarded by API Gateway")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> verifyHeaders(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("X-User-Id", userId != null ? userId : "MISSING");
        headers.put("X-User-Email", userEmail != null ? userEmail : "MISSING");
        headers.put("X-User-Roles", userRoles != null ? userRoles : "MISSING");
        return ResponseEntity.ok(ApiResponse.ok("Forwarded identity headers", headers));
    }
}
