package com.smashit_beta.backend.service;

import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.exception.InvalidStateException;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

// The matchmaking algorithm (FR-014 to FR-020), kept separate from the database.
// It receives plain Java objects and returns plain Java objects, so it's easy to unit test
// without Mockito: give it players, check who ends up in which seat.
//
// The organizer may choose some seats ("pinned" players). This class fills the
// EMPTY seats with the fairest free players:
//   1. fewest completed matches   (FR-015)
//   2. then longest waiting       (finished their last match earliest, or never played)
//   3. then random                (FR-016)
//
// @Component = "Spring, create one of these and inject it where needed"
// (like @Service, which is just a @Component with a more specific name).
@Component
public class LineupFiller {

    // Doubles: 2 per team, 4 per court (FR-014).
    public static final int PLAYERS_PER_TEAM = 2;
    public static final int PLAYERS_PER_MATCH = PLAYERS_PER_TEAM * 2;

    // One participant's history in this event. Someone with no completed match
    // has 0 matches and lastPlayedAt = null.
    public record PlayerStats(long completedMatches, Instant lastPlayedAt) {
        public static final PlayerStats NONE = new PlayerStats(0, null);
    }

    // One filled seat.
    public record Seat(EventParticipant participant, boolean autoAssigned) {
    }

    // The finished line-up: 2 seats per team.
    public record Lineup(List<Seat> teamA, List<Seat> teamB) {
    }

    private final Random random;

    // Spring uses this constructor: real randomness.
    public LineupFiller() {
        this(new Random());
    }

    // Tests use this one with a fixed "seed", e.g. new Random(42), so the "random"
    // order is the same on every run and the tests are repeatable.
    LineupFiller(Random random) {
        this.random = random;
    }

    // "Who deserves to play next?" as a Comparator (a rule for sorting).
    // Fewest completed matches first; on a tie, whoever finished their last match
    // earliest. nullsFirst = never played comes before everyone who has.
    // Also used by the "next up" queue screen.
    public static Comparator<EventParticipant> fairestFirst(Map<Long, PlayerStats> stats) {
        return Comparator
                .comparingLong((EventParticipant p) -> statsOf(stats, p).completedMatches())
                .thenComparing(p -> statsOf(stats, p).lastPlayedAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    // pinnedA / pinnedB = players the organizer put on each team (0 to 2 each)
    // freePlayers       = everyone else who could play: active, not pinned, not on another court
    // stats             = participant id -> history (missing = never played)
    public Lineup fill(
            List<EventParticipant> pinnedA,
            List<EventParticipant> pinnedB,
            List<EventParticipant> freePlayers,
            Map<Long, PlayerStats> stats
    ) {
        int emptyA = PLAYERS_PER_TEAM - pinnedA.size();
        int emptyB = PLAYERS_PER_TEAM - pinnedB.size();
        int needed = emptyA + emptyB;

        if (freePlayers.size() < needed) {
            throw new InvalidStateException("Not enough free players to fill the court: "
                    + needed + " needed, " + freePlayers.size() + " available.");
        }

        // Step 1 (FR-016): shuffle, so players who are tied end up in random order.
        // (Copy first: never change a list someone else gave us.)
        List<EventParticipant> queue = new ArrayList<>(freePlayers);
        Collections.shuffle(queue, random);

        // Step 2 (FR-015): sort fairest first. Java's List.sort is "stable":
        // players who are tied keep their order from step 1, i.e. random.
        queue.sort(fairestFirst(stats));

        // Step 3 (FR-019): the front of the queue fills the empty seats; everyone else waits.
        List<EventParticipant> picked = new ArrayList<>(queue.subList(0, needed));

        // Step 4: shuffle the picked players before seating them, so the two
        // "fairest" players aren't always partners.
        Collections.shuffle(picked, random);

        // Step 5: organizer's choices first (autoAssigned = false), then the app's picks.
        List<Seat> teamA = new ArrayList<>();
        pinnedA.forEach(player -> teamA.add(new Seat(player, false)));
        picked.subList(0, emptyA).forEach(player -> teamA.add(new Seat(player, true)));

        List<Seat> teamB = new ArrayList<>();
        pinnedB.forEach(player -> teamB.add(new Seat(player, false)));
        picked.subList(emptyA, needed).forEach(player -> teamB.add(new Seat(player, true)));

        return new Lineup(List.copyOf(teamA), List.copyOf(teamB));
    }

    private static PlayerStats statsOf(Map<Long, PlayerStats> stats, EventParticipant participant) {
        return stats.getOrDefault(participant.getId(), PlayerStats.NONE);
    }
}