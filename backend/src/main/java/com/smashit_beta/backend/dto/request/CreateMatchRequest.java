package com.smashit_beta.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// The JSON body for setting up the next match on a court.
// teamA / teamB = participant ids the organizer chose (0 to 2 per team).
// Empty seats are filled by the app. Examples:
//   { "courtNumber": 1 }                                       -> app picks all 4
//   { "courtNumber": 1, "teamA": [12, 15] }                    -> app picks team B
//   { "courtNumber": 2, "teamA": [12], "teamB": [20] }         -> app picks one partner each
//   { "courtNumber": 2, "teamA": [12, 15], "teamB": [20, 21] } -> fully manual
public record CreateMatchRequest(

        @NotNull(message = "Court number is required.")
        @Min(value = 1, message = "Court number must be at least 1.")
        Integer courtNumber,

        // List<@NotNull Long> = the list may be missing, but it can't CONTAIN a null.
        @Size(max = 2, message = "A team has at most 2 players.")
        List<@NotNull Long> teamA,

        @Size(max = 2, message = "A team has at most 2 players.")
        List<@NotNull Long> teamB
) {
}