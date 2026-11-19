package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.MatchStatus;

// One court in the event summary: how many matches it finished, and what's on it now.
// currentMatchId / currentMatchStatus are null when the court is free.
public record CourtSummaryResponse(
        int courtNumber,
        long completedMatches,
        Long currentMatchId,
        MatchStatus currentMatchStatus
) {
}