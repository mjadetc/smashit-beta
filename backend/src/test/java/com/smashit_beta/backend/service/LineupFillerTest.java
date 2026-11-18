package com.smashit_beta.backend.service;

import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.service.LineupFiller.Lineup;
import com.smashit_beta.backend.service.LineupFiller.PlayerStats;
import com.smashit_beta.backend.service.LineupFiller.Seat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plain unit tests: LineupFiller has no database and no dependencies,
// so we don't need Mockito or Spring at all. Just "new" it and call it.
class LineupFillerTest {

    // A fixed seed makes the shuffles the same on every run.
    private final LineupFiller filler = new LineupFiller(new Random(42));

    // Builds participants with ids 1..count, named P1, P2, ...
    // (Their event isn't needed by the filler, so it's null.)
    private List<EventParticipant> players(int count) {
        List<EventParticipant> players = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            EventParticipant participant = new EventParticipant(null, "P" + id);
            ReflectionTestUtils.setField(participant, "id", id);
            players.add(participant);
        }
        return players;
    }

    // Every seat in the line-up, team A first.
    private List<Seat> allSeats(Lineup lineup) {
        return Stream.concat(lineup.teamA().stream(), lineup.teamB().stream()).toList();
    }

    // The ids of the players the app picked.
    private Set<Long> autoPickedIds(Lineup lineup) {
        Set<Long> ids = new HashSet<>();
        allSeats(lineup).stream()
                .filter(Seat::autoAssigned)
                .forEach(seat -> ids.add(seat.participant().getId()));
        return ids;
    }

    @Test
    void shouldFillAllFourSeatsWhenNobodyIsChosen() {
        Lineup lineup = filler.fill(List.of(), List.of(), players(6), Map.of());

        assertEquals(2, lineup.teamA().size());
        assertEquals(2, lineup.teamB().size());
        assertTrue(allSeats(lineup).stream().allMatch(Seat::autoAssigned));
        // FR-017: four DIFFERENT people.
        assertEquals(4, autoPickedIds(lineup).size());
    }

    @Test
    void shouldKeepChosenPairAndPickOpponents() {
        List<EventParticipant> all = players(6);
        // Organizer: P1 + P2 on team A. P3..P6 are free.
        Lineup lineup = filler.fill(List.of(all.get(0), all.get(1)), List.of(), all.subList(2, 6), Map.of());

        // Team A is exactly the chosen pair, marked as the organizer's choice.
        assertEquals(List.of(all.get(0), all.get(1)),
                lineup.teamA().stream().map(Seat::participant).toList());
        assertTrue(lineup.teamA().stream().noneMatch(Seat::autoAssigned));
        // Team B was filled by the app.
        assertTrue(lineup.teamB().stream().allMatch(Seat::autoAssigned));
    }

    @Test
    void shouldPickPartnerForEachChosenPlayer() {
        List<EventParticipant> all = players(6);
        // Organizer: P1 on team A, P2 on team B.
        Lineup lineup = filler.fill(List.of(all.get(0)), List.of(all.get(1)), all.subList(2, 6), Map.of());

        assertEquals(all.get(0), lineup.teamA().get(0).participant());
        assertFalse(lineup.teamA().get(0).autoAssigned());
        assertTrue(lineup.teamA().get(1).autoAssigned());

        assertEquals(all.get(1), lineup.teamB().get(0).participant());
        assertTrue(lineup.teamB().get(1).autoAssigned());
    }

    @Test
    void shouldNotPickAnyoneWhenAllSeatsAreChosen() {
        List<EventParticipant> all = players(4);

        Lineup lineup = filler.fill(all.subList(0, 2), all.subList(2, 4), List.of(), Map.of());

        assertTrue(allSeats(lineup).stream().noneMatch(Seat::autoAssigned));
    }

    @Test
    void shouldPickPlayersWithFewestCompletedMatches() {
        List<EventParticipant> all = players(6);
        // P1..P4 have played 1 match; P5 and P6 have played 3.
        Map<Long, PlayerStats> stats = new HashMap<>();
        for (long id = 1; id <= 4; id++) {
            stats.put(id, new PlayerStats(1, Instant.parse("2026-11-20T11:00:00Z")));
        }
        stats.put(5L, new PlayerStats(3, Instant.parse("2026-11-20T10:00:00Z")));
        stats.put(6L, new PlayerStats(3, Instant.parse("2026-11-20T10:00:00Z")));

        Lineup lineup = filler.fill(List.of(), List.of(), all, stats);

        // FR-015: P5 and P6 wait, even though they've waited longer.
        assertEquals(Set.of(1L, 2L, 3L, 4L), autoPickedIds(lineup));
    }

    @Test
    void shouldPickLongestWaitingWhenCountsAreEqual() {
        List<EventParticipant> all = players(6);
        // Everyone has 1 match. P1 and P2 only just finished; the others finished earlier.
        Map<Long, PlayerStats> stats = new HashMap<>();
        for (long id = 3; id <= 6; id++) {
            stats.put(id, new PlayerStats(1, Instant.parse("2026-11-20T10:00:00Z")));
        }
        stats.put(1L, new PlayerStats(1, Instant.parse("2026-11-20T10:30:00Z")));
        stats.put(2L, new PlayerStats(1, Instant.parse("2026-11-20T10:30:00Z")));

        Lineup lineup = filler.fill(List.of(), List.of(), all, stats);

        assertEquals(Set.of(3L, 4L, 5L, 6L), autoPickedIds(lineup));
    }

    @Test
    void shouldPickRandomlyBetweenTiedPlayers() {
        // 5 tied players, 4 seats: one is left out.
        // FR-016: across many runs, it shouldn't always be the same person.
        List<EventParticipant> all = players(5);
        Set<Long> leftOut = new HashSet<>();

        for (int seed = 0; seed < 50; seed++) {
            Lineup lineup = new LineupFiller(new Random(seed)).fill(List.of(), List.of(), all, Map.of());
            Set<Long> picked = autoPickedIds(lineup);
            all.stream().map(EventParticipant::getId).filter(id -> !picked.contains(id)).forEach(leftOut::add);
        }

        assertTrue(leftOut.size() > 1, "Different players should be left out");
    }

    @Test
    void shouldFailWhenNotEnoughFreePlayers() {
        List<EventParticipant> all = players(4);
        // P1 chosen, so 3 seats are empty, but only 2 free players.
        InvalidStateException ex = assertThrows(InvalidStateException.class,
                () -> filler.fill(List.of(all.get(0)), List.of(), all.subList(1, 3), Map.of()));

        assertEquals("Not enough free players to fill the court: 3 needed, 2 available.", ex.getMessage());
    }
}