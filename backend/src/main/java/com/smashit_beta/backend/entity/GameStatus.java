package com.smashit_beta.backend.entity;

// The life of one game inside a match. Names match chk_match_games_status in V7.
public enum GameStatus {

    // Points are being recorded.
    IN_PROGRESS,

    // A team reached 21 with a 2-point lead (or 30). "Undo" can still reopen it
    // until the organizer completes the match.
    COMPLETED
}