package com.smashit_beta.backend.dto.response;

// NEW (V11): one row of the "Who shares the costs?" list on the expenses screen.
//   playedAMatch       = finished at least one match (part of the default rule)
//   sharesEqually      = the final answer used in the summary
//   decidedByOrganizer = false while the default rule applies; true once the organizer toggled it
public record SharingResponse(
        Long participantId,
        String playerName,
        boolean active,
        boolean playedAMatch,
        boolean sharesEqually,
        boolean decidedByOrganizer
) {
}