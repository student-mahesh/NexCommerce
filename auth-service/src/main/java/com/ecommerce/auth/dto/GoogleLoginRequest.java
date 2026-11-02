package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token or authorization credential is required")
        String idToken,

        String email,
        String name
) {
}
