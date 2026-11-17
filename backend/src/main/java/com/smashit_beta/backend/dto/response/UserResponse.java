
package com.smashit_beta.backend.dto.response;

import java.time.Instant;

public record UserResponse(
        Long id,
        String firstName, 
        String lastName,
        String username,
        String email,
        Instant createdAt
) {
}
