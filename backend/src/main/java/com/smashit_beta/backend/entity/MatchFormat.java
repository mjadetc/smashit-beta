package com.smashit_beta.backend.entity;

// An enum is a type with a fixed list of allowed values.
// Java won't let you create a MatchFormat that isn't one of these two,
// so a typo like "BEST_OF_THRE" fails at compile time instead of at runtime.
// The names match the CHECK constraint in V4__create_events_table.sql exactly.
public enum MatchFormat {

    // Standard rules: best of three games to 21 (FR-022).
    BEST_OF_THREE,

    // Recreational: one game to 21, optional change of ends (FR-023, FR-025).
    SINGLE_GAME
}