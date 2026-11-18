package com.smashit_beta.backend.entity;

// The two sides of the net. Stored as 'A' or 'B' in CHAR(1) columns
// (match_players.team, matches.winning_team).
public enum Team {
    A,
    B
}