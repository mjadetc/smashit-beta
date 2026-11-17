package com.smashit_beta.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

// The JSON body the frontend sends to POST /api/auth/login.
// Example: { "email": "player@example.com", "password": "SmashHit123!" }
public record LoginRequest(

        // Email is the login identifier (see UserDetailsServiceImpl).
        @NotBlank(message = "Email is required.")
        String email,

        // We only check that it is present here; the actual
        // password check happens in Spring Security's AuthenticationManager.
        @NotBlank(message = "Password is required.")
        String password
) {
}