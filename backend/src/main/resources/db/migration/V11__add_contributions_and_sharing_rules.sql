-- V11: Fairer expense sharing.
--  1) An expense can be paid by someone OUTSIDE the event (e.g. a non-playing organizer
--     or a coach), who is either paid back or gave it as a gift.
--  2) Contributions: "Ana puts in 200 for the shuttlecocks". They come off the total
--     BEFORE the rest is split equally.
--  3) Per participant: do they also pay an equal share? (NULL = the default rule)
--  4) Per event: round each equal share up to a whole peso, 5 or 10 pesos.

-- ---------- 1) outside payers ----------
ALTER TABLE expenses
    ALTER COLUMN paid_by_id DROP NOT NULL;

-- The outside payer's name, e.g. 'Coach Jun'. Only set when paid_by_id is NULL.
ALTER TABLE expenses
    ADD COLUMN outside_payer_name VARCHAR(80);

-- TRUE = pay them back; FALSE = a gift, so it comes off the total. Only for outside payers.
ALTER TABLE expenses
    ADD COLUMN outside_payer_repaid BOOLEAN;

-- Exactly one kind of payer: a participant, OR an outside person (name + repaid choice).
ALTER TABLE expenses
    ADD CONSTRAINT chk_expenses_one_payer CHECK (
        (paid_by_id IS NOT NULL AND outside_payer_name IS NULL AND outside_payer_repaid IS NULL)
        OR
        (paid_by_id IS NULL AND outside_payer_name IS NOT NULL AND outside_payer_repaid IS NOT NULL)
    );

-- ---------- 2) contributions ----------
CREATE TABLE contributions (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events (id) ON DELETE CASCADE,

    -- Who contributes. DEFERRABLE: same reason as match_players in V6.
    participant_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,

    -- Optional: what it was "for", only as a label (e.g. the shuttlecocks expense).
    -- ON DELETE SET NULL: deleting that expense keeps the contribution, just without the label.
    expense_id BIGINT REFERENCES expenses (id) ON DELETE SET NULL,

    amount NUMERIC(10, 2) NOT NULL,

    note VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_contributions_amount CHECK (amount > 0)
);

CREATE INDEX idx_contributions_event_id ON contributions (event_id);

-- ---------- 3) shares equally? ----------
-- NULL  = default rule: yes if active OR finished at least one match.
-- TRUE / FALSE = the organizer decided.
ALTER TABLE event_participants
    ADD COLUMN shares_equally BOOLEAN;

-- ---------- 4) rounding ----------
ALTER TABLE events
    ADD COLUMN share_rounding VARCHAR(10) NOT NULL DEFAULT 'NONE';

ALTER TABLE events
    ADD CONSTRAINT chk_events_share_rounding
        CHECK (share_rounding IN ('NONE', 'PESO_1', 'PESO_5', 'PESO_10'));