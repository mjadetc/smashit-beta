-- V9: Event status.
-- ACTIVE    = upcoming or in progress; the organizer can edit it and run rounds.
-- COMPLETED = finished successfully; read-only, hidden from the default list.
-- CANCELLED = called off; read-only, hidden from the default list.
-- Both COMPLETED and CANCELLED events can be reactivated (set back to ACTIVE).
-- "Removed" is NOT a status: removing an event deletes the row (DELETE /api/events/{id}).

-- ALTER TABLE changes an existing table. Existing rows get the DEFAULT value,
-- so every event created before this migration becomes ACTIVE.
ALTER TABLE events
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE events
    ADD CONSTRAINT chk_events_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'));

-- "My events" now always filters by organizer AND status, so one index on both
-- columns replaces the old organizer-only index. (A two-column index still helps
-- queries that filter on its FIRST column alone, so nothing gets slower.)
DROP INDEX idx_events_organizer_id;
CREATE INDEX idx_events_organizer_status ON events (organizer_id, status);