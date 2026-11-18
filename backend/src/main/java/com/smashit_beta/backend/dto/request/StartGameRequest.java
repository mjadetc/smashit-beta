package com.smashit_beta.backend.dto.request;

import com.smashit_beta.backend.entity.Team;

import jakarta.validation.constraints.NotNull;

// FR-026: how the next game starts. The organizer picks, on the court screen:
//   servingTeam = which team serves first (for game 2 and 3: the winner of the previous game)
//   serverId    = who serves first; they stand in their team's RIGHT service court
//   receiverId  = who receives;     they stand in the other team's RIGHT service court
// Their partners take the left service courts. Example:
//   { "servingTeam": "A", "serverId": 12, "receiverId": 20 }
public record StartGameRequest(

        @NotNull(message = "Choose the team that serves first.")
        Team servingTeam,

        @NotNull(message = "Choose the first server.")
        Long serverId,

        @NotNull(message = "Choose the first receiver.")
        Long receiverId
) {
}