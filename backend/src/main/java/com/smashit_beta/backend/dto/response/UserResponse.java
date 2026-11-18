package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.User;

import java.time.Instant;

// The "safe" view of a user that we send back to the frontend.
// There is no passwordHash here: the entity never leaves the backend.
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String username,
        String email,
        Instant createdAt
) {

    // A "static factory method": converts a User entity into a UserResponse.
    // Keeping the conversion in one place means register(), login() and /me
    // all return exactly the same shape.
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}