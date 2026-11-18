package com.smashit_beta.backend.dto.response;

// What the organizer should do next on the court screen, so the frontend
// knows which buttons to show without repeating the rules.
public enum NextStep {

    // The match is SCHEDULED: press "Start match".
    START_MATCH,

    // No game is being played and nobody has won yet: choose the server and receiver.
    START_GAME,

    // A game is being played: tap the team that won each rally.
    RECORD_POINT,

    // A team has won enough games: confirm the result (or undo a mistake first).
    COMPLETE_MATCH,

    // The match is completed or cancelled: nothing left to do.
    NONE
}