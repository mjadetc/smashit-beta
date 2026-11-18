package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.MatchPlayer;

// One seat in a match, e.g. { "participantId": 12, "playerName": "Ana", "autoAssigned": false }
// autoAssigned lets the UI show which seats the app filled (and reshuffle may change).
public record SeatResponse(Long participantId, String playerName, boolean autoAssigned) {

    public static SeatResponse from(MatchPlayer player) {
        return new SeatResponse(
                player.getParticipant().getId(),
                player.getParticipant().getPlayerName(),
                player.isAutoAssigned()
        );
    }
}