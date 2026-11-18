-- V8: Event expenses.
-- Each participant's share is CALCULATED, not stored (FR-041):
--   share = total of the event's expenses / number of active participants.

CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,

    -- The participant who paid upfront.
    -- Blocks hard-deleting a participant who paid for something, checked at
    -- commit time (DEFERRABLE INITIALLY DEFERRED: same reason as match_players in V6).
    paid_by_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,

    description VARCHAR(255) NOT NULL,

    -- Mapped to a Java enum (ExpenseCategory) later.
    category VARCHAR(30) NOT NULL,

    -- NUMERIC(10, 2) = an exact decimal with 2 decimal places, up to 99,999,999.99.
    -- Never use floating-point types (REAL / DOUBLE PRECISION) for money:
    -- they can't store values like 0.10 exactly, which causes rounding errors.
    amount NUMERIC(10, 2) NOT NULL,

    expense_date DATE NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_expenses_amount CHECK (amount > 0),

    CONSTRAINT chk_expenses_category
        CHECK (category IN ('COURT_RENTAL', 'SHUTTLECOCKS', 'FOOD_AND_DRINKS', 'TRANSPORTATION', 'OTHER'))
);

-- Speeds up "all expenses for event X".
CREATE INDEX idx_expenses_event_id ON expenses (event_id);