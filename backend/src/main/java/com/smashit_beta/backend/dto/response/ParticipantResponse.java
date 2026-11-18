package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.EventParticipant;

import java.time.Instant;

// What the frontend receives for a participant.
// (Completed-match counts for FR-036 will be added once matches exist.)
public record ParticipantResponse(
        Long id,
        Long eventId,
        String playerName,
        boolean active,
        Instant createdAt
) {

    public static ParticipantResponse from(EventParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                // Reading only the id doesn't load the lazy Event.
                participant.getEvent().getId(),
                participant.getPlayerName(),
                participant.isActive(),
                participant.getCreatedAt()
        );
    }
}