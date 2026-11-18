-- V10: Replace "rounds" with a rolling queue per court.
-- Each court now runs on its own: when a court is free, the organizer sets up
-- the next match there, without waiting for the other courts.
-- So a match belongs directly to its EVENT (and a court), not to a round.

-- 1) Give every match its event directly.
--    Added as nullable first, filled in from the old round, then made NOT NULL.
ALTER TABLE matches
    ADD COLUMN event_id BIGINT REFERENCES events (id) ON DELETE CASCADE;

UPDATE matches m
SET event_id = r.event_id
FROM rounds r
WHERE r.id = m.round_id;

ALTER TABLE matches
    ALTER COLUMN event_id SET NOT NULL;

-- 2) Remove rounds. Dropping round_id also drops uq_matches_round_court,
--    because that constraint used the column.
ALTER TABLE matches
    DROP COLUMN round_id;

DROP TABLE rounds;

-- 3) One unfinished match per court at a time.
--    A "partial" unique index only applies to rows matching its WHERE clause,
--    so a court can have many COMPLETED or CANCELLED matches, but only one
--    SCHEDULED or IN_PROGRESS match.
CREATE UNIQUE INDEX uq_matches_open_court
    ON matches (event_id, court_number)
    WHERE status IN ('SCHEDULED', 'IN_PROGRESS');

-- Speeds up "all matches of this event".
CREATE INDEX idx_matches_event_id ON matches (event_id);

-- 4) Remember who chose each seat.
--    FALSE = the organizer picked this player; TRUE = the app filled the seat.
--    "Reshuffle" only replaces the seats the app filled.
ALTER TABLE match_players
    ADD COLUMN auto_assigned BOOLEAN NOT NULL DEFAULT FALSE;