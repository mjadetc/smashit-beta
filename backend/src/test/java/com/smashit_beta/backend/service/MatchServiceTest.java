package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.CreateMatchRequest;
import com.smashit_beta.backend.dto.response.MatchResponse;
import com.smashit_beta.backend.dto.response.SeatResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.entity.Team;
import com.smashit_beta.backend.entity.User;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.EventParticipantRepository;
import com.smashit_beta.backend.repository.MatchPlayerRepository;
import com.smashit_beta.backend.repository.MatchRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UNIT tests for MatchService. The repositories and EventService are fakes.
// LineupFiller is the REAL class (with a fixed seed): it has no database,
// so there's no reason to fake it, and this also checks the two work together.
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final String EMAIL = "organizer@example.com";
    private static final Long EVENT_ID = 5L;

    @Mock
    private EventService eventService;

    @Mock
    private EventParticipantRepository participantRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchPlayerRepository matchPlayerRepository;

    // Not @InjectMocks: we build it by hand so we can pass the real, seeded LineupFiller.
    private MatchService matchService;

    private Event event;

    // P1..Pn, filled in by buildPlayers() / givenParticipants().
    private List<EventParticipant> players;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(eventService, participantRepository, matchRepository,
                matchPlayerRepository, new LineupFiller(new Random(7)));

        User organizer = new User("Test", "Organizer", "organizer", EMAIL, "hash");
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 20, 19, 0, 0, 0, ZoneOffset.ofHours(8));
        // 2 courts
        event = new Event(organizer, "Friday Smash", "Court Hub", start, start.plusHours(3),
                2, MatchFormat.SINGLE_GAME, null, null);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
    }

    private void givenOwnedEvent() {
        when(eventService.findOwnedEvent(EMAIL, EVENT_ID)).thenReturn(event);
    }

    // Builds participants P1, P2, ... WITHOUT telling the repository about them.
    // (For tests that only need them inside an existing match.)
    private void buildPlayers(int count) {
        players = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            EventParticipant participant = new EventParticipant(event, "P" + id);
            ReflectionTestUtils.setField(participant, "id", id);
            players.add(participant);
        }
    }

    // The event has `count` active participants, named P1, P2, ...
    private void givenParticipants(int count) {
        buildPlayers(count);
        when(participantRepository.findByEventIdAndActiveTrueOrderByPlayerNameAsc(EVENT_ID)).thenReturn(players);
    }

    // Nobody has completed a match yet. Kept separate from givenParticipants because
    // Mockito (strict mode) fails a test that stubs something it never uses.
    private void givenNoCompletedMatches() {
        when(matchPlayerRepository.findStatsPerParticipant(EVENT_ID, MatchStatus.COMPLETED))
                .thenReturn(List.of());
    }

    // These matches are currently open (SCHEDULED or IN_PROGRESS).
    private void givenOpenMatches(Match... matches) {
        when(matchRepository.findByEventIdAndStatusIn(eq(EVENT_ID), anyList())).thenReturn(List.of(matches));
    }

    // A match "already in the database" with P1, P2 vs P3, P4 (all chosen by the organizer).
    private Match existingMatch(Long id, int court) {
        Match match = new Match(event, court);
        ReflectionTestUtils.setField(match, "id", id);
        match.addPlayer(players.get(0), Team.A, false);
        match.addPlayer(players.get(1), Team.A, false);
        match.addPlayer(players.get(2), Team.B, false);
        match.addPlayer(players.get(3), Team.B, false);
        return match;
    }

    @Test
    void shouldCreateMatchWithChosenPairAndAutoOpponents() {
        givenOwnedEvent();
        givenParticipants(6);
        givenNoCompletedMatches();
        givenOpenMatches();
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResponse response = matchService.create(EMAIL, EVENT_ID,
                new CreateMatchRequest(1, List.of(1L, 2L), null));

        assertEquals(MatchStatus.SCHEDULED, response.status());
        assertEquals(1, response.courtNumber());
        // Team A is the chosen pair...
        assertEquals(List.of(1L, 2L), response.teamA().stream().map(SeatResponse::participantId).toList());
        assertTrue(response.teamA().stream().noneMatch(SeatResponse::autoAssigned));
        // ...and team B was picked by the app.
        assertEquals(2, response.teamB().size());
        assertTrue(response.teamB().stream().allMatch(SeatResponse::autoAssigned));
    }

    @Test
    void shouldRejectCourtThatIsInUse() {
        givenOwnedEvent();
        // The court check fails before anyone looks at participants.
        buildPlayers(8);
        givenOpenMatches(existingMatch(40L, 1));

        InvalidStateException ex = assertThrows(InvalidStateException.class,
                () -> matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(1, null, null)));

        assertEquals("Court 1 already has a match that hasn't finished.", ex.getMessage());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void shouldRejectCourtNumberAboveEventsCourts() {
        givenOwnedEvent();

        // The event has 2 courts.
        assertThrows(IllegalArgumentException.class,
                () -> matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(3, null, null)));
    }

    @Test
    void shouldRejectPlayerWhoIsOnAnotherCourt() {
        givenOwnedEvent();
        givenParticipants(8);
        // P1..P4 are on court 1.
        givenOpenMatches(existingMatch(40L, 1));

        InvalidStateException ex = assertThrows(InvalidStateException.class,
                () -> matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(2, List.of(1L), null)));

        assertEquals("\"P1\" is already in a match on court 1.", ex.getMessage());
    }

    @Test
    void shouldNotPickPlayersWhoAreOnAnotherCourt() {
        givenOwnedEvent();
        givenParticipants(8);
        givenNoCompletedMatches();
        // P1..P4 are on court 1, so court 2 must get exactly P5..P8.
        givenOpenMatches(existingMatch(40L, 1));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResponse response = matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(2, null, null));

        List<Long> ids = new ArrayList<>();
        response.teamA().forEach(seat -> ids.add(seat.participantId()));
        response.teamB().forEach(seat -> ids.add(seat.participantId()));
        assertTrue(ids.containsAll(List.of(5L, 6L, 7L, 8L)));
    }

    @Test
    void shouldRejectSamePlayerInTwoSeats() {
        givenOwnedEvent();

        assertThrows(IllegalArgumentException.class,
                () -> matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(1, List.of(1L), List.of(1L))));
    }

    @Test
    void shouldRejectChosenPlayerFromAnotherEvent() {
        givenOwnedEvent();
        givenParticipants(4);
        givenOpenMatches();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(1, List.of(99L), null)));

        assertEquals("Participant 99 is not an active player in this event.", ex.getMessage());
    }

    @Test
    void shouldNotCreateMatchForCancelledEvent() {
        event.cancel();
        givenOwnedEvent();

        assertThrows(InvalidStateException.class,
                () -> matchService.create(EMAIL, EVENT_ID, new CreateMatchRequest(1, null, null)));
    }

    @Test
    void shouldStartScheduledMatch() {
        givenOwnedEvent();
        buildPlayers(4);
        Match match = existingMatch(40L, 1);
        when(matchRepository.findByIdAndEventId(40L, EVENT_ID)).thenReturn(Optional.of(match));

        MatchResponse response = matchService.start(EMAIL, EVENT_ID, 40L);

        assertEquals(MatchStatus.IN_PROGRESS, response.status());
        assertNotNull(response.startedAt());
    }

    @Test
    void shouldNotReshuffleMatchThatAlreadyStarted() {
        givenOwnedEvent();
        buildPlayers(4);
        Match match = existingMatch(40L, 1);
        match.start();
        when(matchRepository.findByIdAndEventId(40L, EVENT_ID)).thenReturn(Optional.of(match));

        InvalidStateException ex = assertThrows(InvalidStateException.class,
                () -> matchService.reshuffle(EMAIL, EVENT_ID, 40L));

        assertEquals("The line-up can only be changed before the match starts.", ex.getMessage());
    }

    @Test
    void shouldCancelMatch() {
        givenOwnedEvent();
        buildPlayers(4);
        Match match = existingMatch(40L, 1);
        when(matchRepository.findByIdAndEventId(40L, EVENT_ID)).thenReturn(Optional.of(match));

        MatchResponse response = matchService.cancel(EMAIL, EVENT_ID, 40L);

        assertEquals(MatchStatus.CANCELLED, response.status());
    }

    @Test
    void shouldNotFindMatchFromAnotherEvent() {
        givenOwnedEvent();
        when(matchRepository.findByIdAndEventId(99L, EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> matchService.start(EMAIL, EVENT_ID, 99L));
    }
}