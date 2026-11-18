package com.smashit_beta.backend.entity;

// The life of one match on one court. Names match chk_matches_status in V6.
public enum MatchStatus {

    // Players assigned, not started yet. The line-up can still change.
    SCHEDULED,

    // Players are on court (scoring happens in the next branch).
    IN_PROGRESS,

    // Finished with a winner. ONLY these count for matchmaking fairness (FR-020).
    COMPLETED,

    // Abandoned. Doesn't count for anything (FR-035).
    CANCELLED
}