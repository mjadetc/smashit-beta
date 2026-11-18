-- V6: Rounds -> matches (one per court) -> the 4 players in each match.

CREATE TABLE rounds (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,

    round_number INT NOT NULL,

    -- DRAFT       = generated; the organizer can still regenerate it
    --               (flowchart step "Generated Round Good?")
    -- IN_PROGRESS = matches have started
    -- COMPLETED   = every match is finished
    -- CANCELLED   = abandoned
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_rounds_round_number CHECK (round_number > 0),

    CONSTRAINT chk_rounds_status
        CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),

    -- Round numbers can't repeat within the same event.
    CONSTRAINT uq_rounds_event_round_number UNIQUE (event_id, round_number)
);

CREATE TABLE matches (
    id BIGSERIAL PRIMARY KEY,
    round_id BIGINT NOT NULL REFERENCES rounds (id) ON DELETE CASCADE,

    -- Which court this match is on (1 to events.court_count; the upper limit is checked in Java).
    court_number INT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',

    -- 'A' or 'B' once the match is completed; NULL before that.
    winning_team CHAR(1),

    -- NULL until the match starts / finishes.
    -- Duration (FR-033) is calculated as completed_at - started_at.
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_matches_court_number CHECK (court_number > 0),

    CONSTRAINT chk_matches_status
        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),

    CONSTRAINT chk_matches_winning_team
        CHECK (winning_team IS NULL OR winning_team IN ('A', 'B')),

    -- A COMPLETED match must have a winner and a completion time.
    CONSTRAINT chk_matches_completed_has_result
        CHECK (status <> 'COMPLETED' OR (winning_team IS NOT NULL AND completed_at IS NOT NULL)),

    -- A match can't finish before it starts.
    CONSTRAINT chk_matches_time_order
        CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at)),

    -- Two matches in the same round can't share a court.
    CONSTRAINT uq_matches_round_court UNIQUE (round_id, court_number)
);

CREATE TABLE match_players (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches (id) ON DELETE CASCADE,

    -- No "ON DELETE CASCADE" here: you can't hard-delete a participant who has
    -- played a match (the app soft-deletes them with active = FALSE instead).
    -- DEFERRABLE INITIALLY DEFERRED = check this rule when the transaction COMMITS,
    -- not immediately. That lets deleting a whole EVENT work: by commit time its
    -- cascades have already removed the match_players rows too.
    participant_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,

    -- Which side of the net: 'A' or 'B'.
    team CHAR(1) NOT NULL,

    CONSTRAINT chk_match_players_team CHECK (team IN ('A', 'B')),

    -- The same person can't appear twice in one match.
    CONSTRAINT uq_match_players_match_participant UNIQUE (match_id, participant_id)
);

-- Speeds up "how many completed matches has this participant played?" (FR-015).
CREATE INDEX idx_match_players_participant_id ON match_players (participant_id);