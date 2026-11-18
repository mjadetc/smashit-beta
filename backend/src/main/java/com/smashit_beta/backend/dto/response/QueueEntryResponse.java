package com.smashit_beta.backend.dto.response;

import java.time.Instant;

// One line of the "next up" list: a free player, and why they're in that position.
// lastPlayedAt is null if they haven't finished a match yet.
public record QueueEntryResponse(
        Long participantId,
        String playerName,
        long completedMatches,
        Instant lastPlayedAt
) {
}