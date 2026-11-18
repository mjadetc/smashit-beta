package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.EventParticipant;

// A player's id and name, e.g. { "participantId": 12, "playerName": "Leo" }.
// Used wherever the court screen shows a person (server, receiver, service courts).
public record PlayerRef(Long participantId, String playerName) {

    public static PlayerRef from(EventParticipant participant) {
        return new PlayerRef(participant.getId(), participant.getPlayerName());
    }
}