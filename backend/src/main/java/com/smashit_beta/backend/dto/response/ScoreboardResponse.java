package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.entity.Team;

import java.time.Instant;
import java.util.List;

// Everything the court screen needs for one match, returned by every scoring call,
// so the frontend can redraw from one response after each tap.
public record ScoreboardResponse(
        Long matchId,
        Long eventId,
        int courtNumber,
        MatchFormat matchFormat,
        MatchStatus status,
        // only these 4 people can be chosen as server/receiver
        List<SeatResponse> teamA,
        List<SeatResponse> teamB,
        // every game so far (FR-034)
        List<GameSummaryResponse> games,
        // the latest game in detail, or null before game 1 starts
        LiveGameResponse currentGame,
        // the team that has won enough games, or null (FR-032).
        // Can be set while status is still IN_PROGRESS: the organizer then confirms it.
        Team matchWinner,
        NextStep nextStep,
        // FR-033: start, end, and duration (null until the match is completed)
        Instant startedAt,
        Instant completedAt,
        Long durationSeconds
) {
}