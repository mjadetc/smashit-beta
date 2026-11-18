-- V5: People playing in an event. Participants are guests, not user accounts (FR-013).

CREATE TABLE event_participants (
    id BIGSERIAL PRIMARY KEY,

    -- Deleting an event deletes its participants.
    event_id BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,

    player_name VARCHAR(80) NOT NULL,

    -- "Soft delete": removing a participant sets active = FALSE instead of deleting
    -- the row, so their match history and expenses stay intact.
    -- Only active participants are picked for matches and split expenses.
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- No two participants in the SAME event may share a name, ignoring case
-- ("Juan" and "juan" can't both be added). Different events can reuse names.
-- Re-adding a removed player should re-activate their old row (done in Java later).
-- Because event_id is the index's first column, this index also speeds up
-- "get all participants of event X".
CREATE UNIQUE INDEX uq_event_participants_event_name
    ON event_participants (event_id, LOWER(player_name));