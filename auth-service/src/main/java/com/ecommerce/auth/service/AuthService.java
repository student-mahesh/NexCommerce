package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.GoogleLoginRequest;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.exception.DuplicateResourceException;
import com.ecommerce.auth.exception.ResourceNotFoundException;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting registration for email={}", request.email());

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email is already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved);
        log.info("User registered successfully with id={}", saved.getId());

        return new AuthResponse(token, saved.getId(), saved.getEmail(), saved.getName(), saved.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for email={}", request.email());

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user);
        log.info("Login successful for userId={}", user.getId());

        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        log.info("Processing Google OAuth2 login");

        // Determine user identity from Google token or provided profile
        String email = (request.email() != null && !request.email().isBlank())
                ? request.email().trim().toLowerCase()
                : "google_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        String name = (request.name() != null && !request.name().isBlank())
                ? request.name().trim()
                : "Google User";

        String googleSub = request.idToken().length() > 30
                ? request.idToken().substring(0, 30)
                : request.idToken();

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .name(name)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .googleId(googleSub)
                        .role(Role.ROLE_CUSTOMER)
                        .build()));

        String token = jwtUtil.generateToken(user);
        log.info("Google OAuth2 login completed for userId={}", user.getId());

        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }

    public AuthResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return new AuthResponse(null, user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }
}
