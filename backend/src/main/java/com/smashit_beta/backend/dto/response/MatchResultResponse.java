package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.Team;

import java.time.Instant;
import java.util.List;

// FR-034 / FR-038: one finished match in the event summary, e.g.
// "Court 1: Leo & Ana beat France & Bea 21-18, 19-21, 21-15 (34 min)".
public record MatchResultResponse(
        Long matchId,
        int courtNumber,
        List<PlayerRef> teamA,
        List<PlayerRef> teamB,
        Team winningTeam,
        List<GameSummaryResponse> games,
        Long durationSeconds,
        Instant completedAt
) {
}