-- V4: Badminton events. Each event belongs to exactly one organizer (a user).

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,

    -- The user who created the event (FR-004: ownership).
    -- ON DELETE CASCADE: if a user is ever deleted, their events are deleted too.
    organizer_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    name VARCHAR(120) NOT NULL,
    location VARCHAR(255) NOT NULL,

    -- Full date + time + time zone, instead of separate date/time columns.
    -- This handles sessions that cross midnight (e.g. 10:00 PM to 12:00 AM).
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,

    court_count INT NOT NULL,

    -- Stored as readable text; mapped to a Java enum (MatchFormat) later.
    match_format VARCHAR(20) NOT NULL,

    -- FR-025: optional change-of-ends point for the single-game format
    -- (e.g. 11 = switch ends when a team reaches 11). NULL = no switch.
    change_ends_at INT,

    -- TEXT has no length limit. Description is optional (nullable).
    description TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- CHECK constraints: the database itself refuses invalid rows,
    -- even if a bug in the Java code tries to save them.
    CONSTRAINT chk_events_time_range
        CHECK (ends_at > starts_at),

    CONSTRAINT chk_events_court_count
        CHECK (court_count BETWEEN 1 AND 50),

    CONSTRAINT chk_events_match_format
        CHECK (match_format IN ('BEST_OF_THREE', 'SINGLE_GAME')),

    -- change_ends_at is only allowed for SINGLE_GAME
    -- (BEST_OF_THREE always follows the official change-of-ends rules).
    CONSTRAINT chk_events_change_ends_at
        CHECK (
            change_ends_at IS NULL
            OR (match_format = 'SINGLE_GAME' AND change_ends_at BETWEEN 1 AND 29)
        )
);

-- PostgreSQL does NOT index foreign keys automatically.
-- "Show me my events" filters by organizer_id, so we index it.
CREATE INDEX idx_events_organizer_id ON events (organizer_id);