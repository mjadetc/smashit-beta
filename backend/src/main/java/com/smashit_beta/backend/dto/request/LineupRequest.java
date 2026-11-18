package com.smashit_beta.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// The JSON body for changing a SCHEDULED match's line-up ("plans changed").
// Same rules as CreateMatchRequest, minus the court: list the players you
// want on each team, and the app fills any seats you leave empty.
//   { "teamA": [12, 30], "teamB": [20] }
public record LineupRequest(

        @Size(max = 2, message = "A team has at most 2 players.")
        List<@NotNull Long> teamA,

        @Size(max = 2, message = "A team has at most 2 players.")
        List<@NotNull Long> teamB
) {
}