package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.entity.Team;

import java.time.Instant;
import java.util.List;

// One match as the frontend sees it, e.g.
// { "courtNumber": 1, "status": "SCHEDULED", "teamA": [Ana, Leo], "teamB": [France, Bea] }
public record MatchResponse(
        Long id,
        Long eventId,
        int courtNumber,
        MatchStatus status,
        List<SeatResponse> teamA,
        List<SeatResponse> teamB,
        // null until the match is completed
        Team winningTeam,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {

    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getEvent().getId(),
                match.getCourtNumber(),
                match.getStatus(),
                // .map(SeatResponse::from) = "call SeatResponse.from(...) on each seat"
                match.playersOn(Team.A).stream().map(SeatResponse::from).toList(),
                match.playersOn(Team.B).stream().map(SeatResponse::from).toList(),
                match.getWinningTeam(),
                match.getStartedAt(),
                match.getCompletedAt(),
                match.getCreatedAt()
        );
    }
}