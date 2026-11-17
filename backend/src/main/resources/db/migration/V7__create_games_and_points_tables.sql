-- V7: Games inside a match, plus a log of every point (rally) in each game.

CREATE TABLE match_games (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches (id) ON DELETE CASCADE,

    -- Always 1 for single-game matches; 1 to 3 for best-of-three.
    game_number INT NOT NULL,

    -- Current score. A quick-to-read copy of the latest row in game_points.
    team_a_score INT NOT NULL DEFAULT 0,
    team_b_score INT NOT NULL DEFAULT 0,

    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    winning_team CHAR(1),

    -- FR-026: who serves first in this game. Every later serving state
    -- (server, receiver, who stands left/right) is CALCULATED from these
    -- three columns plus the points played so far.
    -- (DEFERRABLE INITIALLY DEFERRED: same reason as match_players in V6.)
    initial_serving_team CHAR(1) NOT NULL,
    initial_server_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,
    initial_receiver_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,

    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_match_games_game_number CHECK (game_number BETWEEN 1 AND 3),

    -- A badminton game never goes past 30 points (at 29-all, the next point wins).
    CONSTRAINT chk_match_games_scores
        CHECK (team_a_score BETWEEN 0 AND 30 AND team_b_score BETWEEN 0 AND 30),

    CONSTRAINT chk_match_games_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),

    CONSTRAINT chk_match_games_winning_team
        CHECK (winning_team IS NULL OR winning_team IN ('A', 'B')),

    CONSTRAINT chk_match_games_serving_team CHECK (initial_serving_team IN ('A', 'B')),

    CONSTRAINT chk_match_games_completed_has_winner
        CHECK (status <> 'COMPLETED' OR winning_team IS NOT NULL),

    -- A match can't have two "game 2"s.
    CONSTRAINT uq_match_games_match_game_number UNIQUE (match_id, game_number)
);

-- Every rally, in order. Rows are only ever added (or the latest one deleted
-- for "undo last point"), never edited, so this table has no updated_at.
CREATE TABLE game_points (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES match_games (id) ON DELETE CASCADE,

    -- 1, 2, 3, ... The highest number is the latest point.
    sequence_number INT NOT NULL,

    -- Which team won the rally.
    scoring_team CHAR(1) NOT NULL,

    -- Who served and who received this rally (a snapshot for match history).
    server_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,
    receiver_id BIGINT NOT NULL REFERENCES event_participants (id) DEFERRABLE INITIALLY DEFERRED,

    -- The score AFTER this point.
    team_a_score INT NOT NULL,
    team_b_score INT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_game_points_sequence CHECK (sequence_number > 0),

    CONSTRAINT chk_game_points_scoring_team CHECK (scoring_team IN ('A', 'B')),

    CONSTRAINT chk_game_points_scores
        CHECK (team_a_score BETWEEN 0 AND 30 AND team_b_score BETWEEN 0 AND 30),

    -- Each sequence number appears once per game.
    CONSTRAINT uq_game_points_game_sequence UNIQUE (game_id, sequence_number)
);