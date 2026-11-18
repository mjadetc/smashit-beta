package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.GameStatus;
import com.smashit_beta.backend.entity.Team;

// The latest game as the court screen draws it (FR-027, FR-028, FR-030).
// "Right/left court" = the service court, seen by a player facing the net.
public record LiveGameResponse(
        int gameNumber,
        int teamAScore,
        int teamBScore,
        GameStatus status,
        // null while the game is in progress
        Team winningTeam,
        // who serves the NEXT rally, and who receives it
        Team servingTeam,
        PlayerRef server,
        PlayerRef receiver,
        PlayerRef teamARightCourt,
        PlayerRef teamALeftCourt,
        PlayerRef teamBRightCourt,
        PlayerRef teamBLeftCourt,
        // true = the teams are on the opposite ends to where they started game 1
        boolean endsChanged,
        // true = the last rally means "change ends now" (show a pop-up)
        boolean changeEndsNow,
        int ralliesPlayed
) {
}