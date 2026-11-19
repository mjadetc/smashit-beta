package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.EventStatus;
import com.smashit_beta.backend.entity.MatchFormat;

import java.util.List;

// FR-038: the event at a glance. Durations are in seconds, null if nothing has finished.
public record EventSummaryResponse(
        Long eventId,
        String eventName,
        EventStatus status,
        MatchFormat matchFormat,
        int activeParticipants,
        // how many matches are in each status
        long scheduledMatches,
        long inProgressMatches,
        long completedMatches,
        long cancelledMatches,
        // across completed matches only
        long gamesPlayed,
        long ralliesPlayed,
        Long averageMatchDurationSeconds,
        Long longestMatchDurationSeconds,
        // court 1 .. courtCount
        List<CourtSummaryResponse> courts,
        // completed matches, most recent first
        List<MatchResultResponse> results
) {
}