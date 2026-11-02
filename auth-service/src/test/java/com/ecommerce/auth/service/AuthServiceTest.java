package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.GoogleLoginRequest;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .password("encoded_pass")
                .role(Role.ROLE_CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new user and return JWT token")
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "Password123!");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateToken(testUser)).thenReturn("mock_jwt_token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock_jwt_token");
        assertThat(response.email()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering duplicate email")
    void shouldThrowOnDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "Password123!");
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("test@example.com", "Password123!");

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "encoded_pass")).thenReturn(true);
        when(jwtUtil.generateToken(testUser)).thenReturn("mock_jwt_token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock_jwt_token");
    }

    @Test
    @DisplayName("Should throw BadCredentialsException on invalid password")
    void shouldThrowOnInvalidPassword() {
        LoginRequest request = new LoginRequest("test@example.com", "WrongPassword");

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "encoded_pass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should successfully login with Google for existing user")
    void shouldLoginWithGoogleExistingUser() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-token-1234567890", "test@example.com", "Test User");

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken(testUser)).thenReturn("mock_google_jwt_token");

        AuthResponse response = authService.googleLogin(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock_google_jwt_token");
        assertThat(response.email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should successfully register and login new user via Google")
    void shouldRegisterAndLoginNewGoogleUser() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-token-99999", "newgoogle@example.com", "New Google User");

        User newGoogleUser = User.builder()
                .id(2L)
                .name("New Google User")
                .email("newgoogle@example.com")
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.findByEmailIgnoreCase("newgoogle@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(String.class))).thenReturn("random_hashed_pw");
        when(userRepository.save(any(User.class))).thenReturn(newGoogleUser);
        when(jwtUtil.generateToken(newGoogleUser)).thenReturn("mock_new_google_jwt");

        AuthResponse response = authService.googleLogin(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock_new_google_jwt");
        assertThat(response.email()).isEqualTo("newgoogle@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should retrieve profile for valid user ID")
    void shouldGetProfileSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        AuthResponse response = authService.getProfile(1L);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.role()).isEqualTo("ROLE_CUSTOMER");
    }
}
