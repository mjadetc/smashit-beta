
package com.smashit_beta.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "First name is required.")
        @Size(max = 255, message = "Display name must not exceed 255 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 255, message = "Display name must not exceed 255 characters.")
        String lastName,

        @NotBlank(message = "Username is required.")
        @Size(max = 80, message = "Username must not exceed 80 characters.")
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Please provide a valid email address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72,
                message = "Password must be between 8 and 72 characters.")
        String password
    ) {


    }
