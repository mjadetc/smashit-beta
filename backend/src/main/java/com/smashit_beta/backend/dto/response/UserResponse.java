
package com.smashit_beta.backend.dto.response;

import java.time.Instant;

public record UserResponse(
        Long id,
        String firstName, 
        String lastName,
        String userName,
        String email,
        Instant createdAt
) {
}
