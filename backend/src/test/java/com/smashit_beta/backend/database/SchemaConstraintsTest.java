package com.smashit_beta.backend.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Checks that the Flyway migrations (V4-V8) created tables whose constraints
// really protect the data. We have no entities for these tables yet, so we talk to
// PostgreSQL with plain SQL through JdbcTemplate (Spring's helper for running SQL).
//
// IMPORTANT PostgreSQL detail: after ANY error inside a transaction, PostgreSQL
// rejects every later statement in that transaction. So in each test that expects
// an error, the failing statement is always the LAST statement.
@SpringBootTest
@Transactional // Rolls everything back after each test, so nothing stays in your database.
class SchemaConstraintsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Every test needs a user to own its events; @BeforeEach runs before EACH test.
    private Long organizerId;

    @BeforeEach
    void createOrganizer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // "INSERT ... RETURNING id" inserts a row and gives back its new id.
        // Each ? is filled in, in order, by the values after the SQL.
        organizerId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (first_name, last_name, username, email, password_hash)
                VALUES ('Schema', 'Tester', ?, ?, 'test-hash')
                RETURNING id
                """,
                Long.class,
                "schema_" + suffix,
                "schema_" + suffix + "@example.com"
        );
    }

    // ---------- helper methods that insert rows and return their ids ----------

    private Long insertEvent(String matchFormat, Integer changeEndsAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO events (organizer_id, name, location, starts_at, ends_at,
                                    court_count, match_format, change_ends_at)
                VALUES (?, 'Friday Smash', 'Court Hub', NOW(), NOW() + INTERVAL '2 hours',
                        2, ?, ?)
                RETURNING id
                """,
                Long.class,
                organizerId, matchFormat, changeEndsAt
        );
    }

    private Long insertParticipant(Long eventId, String playerName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO event_participants (event_id, player_name) VALUES (?, ?) RETURNING id",
                Long.class,
                eventId, playerName
        );
    }

    private Long insertRound(Long eventId, int roundNumber) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO rounds (event_id, round_number) VALUES (?, ?) RETURNING id",
                Long.class,
                eventId, roundNumber
        );
    }

    private Long insertMatch(Long roundId, int courtNumber) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO matches (round_id, court_number) VALUES (?, ?) RETURNING id",
                Long.class,
                roundId, courtNumber
        );
    }

    private void insertMatchPlayer(Long matchId, Long participantId, String team) {
        jdbcTemplate.update(
                "INSERT INTO match_players (match_id, participant_id, team) VALUES (?, ?, ?)",
                matchId, participantId, team
        );
    }

    // Counts rows in a table that belong to one event-related id.
    private int count(String sql, Long id) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return result == null ? 0 : result;
    }

    // ---------- events ----------

    @Test
    void shouldCreateValidEvent() {
        Long eventId = insertEvent("SINGLE_GAME", 11);
        assertNotNull(eventId);
    }

    @Test
    void shouldRejectEventEndingBeforeItStarts() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO events (organizer_id, name, location, starts_at, ends_at,
                                    court_count, match_format)
                VALUES (?, 'Bad Times', 'Court Hub', NOW(), NOW() - INTERVAL '1 hour',
                        2, 'SINGLE_GAME')
                """,
                organizerId
        ));
    }

    @Test
    void shouldRejectUnknownMatchFormat() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertEvent("BEST_OF_FIVE", null));
    }

    @Test
    void shouldRejectChangeEndsAtForBestOfThree() {
        // change_ends_at is only allowed for SINGLE_GAME.
        assertThrows(DataIntegrityViolationException.class,
                () -> insertEvent("BEST_OF_THREE", 11));
    }

    // ---------- participants ----------

    @Test
    void shouldRejectDuplicateParticipantNameInSameEventIgnoringCase() {
        Long eventId = insertEvent("SINGLE_GAME", null);
        insertParticipant(eventId, "Juan");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertParticipant(eventId, "JUAN"));
    }

    @Test
    void shouldAllowSameParticipantNameInDifferentEvents() {
        Long firstEvent = insertEvent("SINGLE_GAME", null);
        Long secondEvent = insertEvent("BEST_OF_THREE", null);

        insertParticipant(firstEvent, "Juan");
        Long secondJuan = insertParticipant(secondEvent, "Juan");

        assertNotNull(secondJuan);
    }

    @Test
    void shouldBlockHardDeletingParticipantWhoPlayedAMatch() {
        Long eventId = insertEvent("SINGLE_GAME", null);
        Long participantId = insertParticipant(eventId, "Maria");
        Long matchId = insertMatch(insertRound(eventId, 1), 1);
        insertMatchPlayer(matchId, participantId, "A");

        // The foreign key is DEFERRED (checked at commit), but this test rolls back
        // instead of committing. This command makes PostgreSQL check it right away.
        jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");

        // Participants with history must be soft-deleted (active = FALSE) instead.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "DELETE FROM event_participants WHERE id = ?", participantId
        ));
    }

    // ---------- rounds and matches ----------

    @Test
    void shouldRejectTwoMatchesOnSameCourtInSameRound() {
        Long roundId = insertRound(insertEvent("SINGLE_GAME", null), 1);
        insertMatch(roundId, 1);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertMatch(roundId, 1));
    }

    @Test
    void shouldRejectCompletedMatchWithoutWinner() {
        Long matchId = insertMatch(insertRound(insertEvent("SINGLE_GAME", null), 1), 1);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE matches SET status = 'COMPLETED' WHERE id = ?", matchId
        ));
    }

    // ---------- expenses ----------

    @Test
    void shouldRejectZeroOrNegativeExpenseAmount() {
        Long eventId = insertEvent("SINGLE_GAME", null);
        Long payerId = insertParticipant(eventId, "Ana");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO expenses (event_id, paid_by_id, description, category, amount, expense_date)
                VALUES (?, ?, 'Shuttlecocks', 'SHUTTLECOCKS', ?, CURRENT_DATE)
                """,
                eventId, payerId, new BigDecimal("0.00")
        ));
    }

    // ---------- deleting an event (FR-008) ----------

    @Test
    void shouldDeleteEverythingWhenEventIsDeleted() {
        // Build a small but complete event: participant, round, match, player, expense.
        Long eventId = insertEvent("SINGLE_GAME", null);
        Long participantId = insertParticipant(eventId, "Leo");
        Long matchId = insertMatch(insertRound(eventId, 1), 1);
        insertMatchPlayer(matchId, participantId, "A");
        jdbcTemplate.update(
                """
                INSERT INTO expenses (event_id, paid_by_id, description, category, amount, expense_date)
                VALUES (?, ?, 'Court fee', 'COURT_RENTAL', 500.00, CURRENT_DATE)
                """,
                eventId, participantId
        );

        jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);

        // Run the deferred foreign-key checks NOW (a real commit would run them).
        // If the cascades had left anything behind, this line would throw.
        jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");

        // ON DELETE CASCADE removed every row that belonged to the event.
        assertEquals(0, count("SELECT COUNT(*) FROM event_participants WHERE event_id = ?", eventId));
        assertEquals(0, count("SELECT COUNT(*) FROM rounds WHERE event_id = ?", eventId));
        assertEquals(0, count("SELECT COUNT(*) FROM expenses WHERE event_id = ?", eventId));
        assertEquals(0, count("SELECT COUNT(*) FROM match_players WHERE match_id = ?", matchId));
    }

    @Test
    void shouldDefaultNewEventsToActive() {
        Long eventId = insertEvent("SINGLE_GAME", null);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM events WHERE id = ?", String.class, eventId);

        assertEquals("ACTIVE", status);
    }

    @Test
    void shouldAllowCompletedAndCancelledStatus() {
        Long eventId = insertEvent("SINGLE_GAME", null);

        // update() returns how many rows changed.
        assertEquals(1, jdbcTemplate.update(
                "UPDATE events SET status = 'COMPLETED' WHERE id = ?", eventId));
        assertEquals(1, jdbcTemplate.update(
                "UPDATE events SET status = 'CANCELLED' WHERE id = ?", eventId));
    }

    @Test
    void shouldRejectUnknownEventStatus() {
        Long eventId = insertEvent("SINGLE_GAME", null);

        // REMOVED is not a status: removed events are deleted instead.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE events SET status = 'REMOVED' WHERE id = ?", eventId
        ));
    }
}