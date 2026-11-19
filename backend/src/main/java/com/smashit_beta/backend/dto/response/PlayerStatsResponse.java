package com.smashit_beta.backend.dto.response;

// FR-036: one player's line in the event's standings.
// Only COMPLETED matches count (FR-035); cancelled or unfinished ones are ignored.
public record PlayerStatsResponse(
        Long participantId,
        String playerName,
        // false = removed from the event (still listed if they finished a match)
        boolean active,
        long matchesPlayed,
        long wins,
        long losses,
        long gamesWon,
        long gamesLost,
        // rally points scored by their team, and against it
        long pointsWon,
        long pointsLost
) {
}