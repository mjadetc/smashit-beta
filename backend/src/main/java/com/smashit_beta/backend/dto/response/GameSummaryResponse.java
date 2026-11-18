package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.GameStatus;
import com.smashit_beta.backend.entity.MatchGame;
import com.smashit_beta.backend.entity.Team;

// One game's result line, e.g. "Game 1: 21-18, won by A" (FR-034).
public record GameSummaryResponse(
        int gameNumber,
        int teamAScore,
        int teamBScore,
        GameStatus status,
        // null while the game is in progress
        Team winningTeam
) {

    public static GameSummaryResponse from(MatchGame game) {
        return new GameSummaryResponse(game.getGameNumber(), game.getTeamAScore(), game.getTeamBScore(),
                game.getStatus(), game.getWinningTeam());
    }
}