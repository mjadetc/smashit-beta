package com.smashit_beta.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The JSON body for adding (POST) or renaming (PUT) a participant.
// Example: { "playerName": "Juan Dela Cruz" }
public record ParticipantRequest(

        @NotBlank(message = "Player name is required.")
        @Size(max = 80, message = "Player name must not exceed 80 characters.")
        String playerName
) {
}