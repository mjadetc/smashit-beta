package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.CreateMatchRequest;
import com.smashit_beta.backend.dto.request.LineupRequest;
import com.smashit_beta.backend.dto.response.MatchResponse;
import com.smashit_beta.backend.dto.response.QueueEntryResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchPlayer;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.entity.Team;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.EventParticipantRepository;
import com.smashit_beta.backend.repository.MatchPlayerRepository;
import com.smashit_beta.backend.repository.MatchPlayerRepository.ParticipantMatchStats;
import com.smashit_beta.backend.repository.MatchRepository;
import com.smashit_beta.backend.service.LineupFiller.Lineup;
import com.smashit_beta.backend.service.LineupFiller.PlayerStats;
import com.smashit_beta.backend.service.LineupFiller.Seat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Running the courts (FR-014 to FR-020) as a rolling queue:
// each court runs on its own, and when one is free the organizer sets up the next match there.
//
//   create (SCHEDULED) -> change line-up / reshuffle (still SCHEDULED) -> start (IN_PROGRESS)
//   -> scoring (next branch) -> COMPLETED          ...or cancel at any point before that.
//
// "Open" match = SCHEDULED or IN_PROGRESS. An open match occupies its court and its 4 players.
@Service
public class MatchService {

    private static final List<MatchStatus> OPEN_STATUSES = List.of(MatchStatus.SCHEDULED, MatchStatus.IN_PROGRESS);

    private final EventService eventService;
    private final EventParticipantRepository participantRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final LineupFiller lineupFiller;

    public MatchService(
            EventService eventService,
            EventParticipantRepository participantRepository,
            MatchRepository matchRepository,
            MatchPlayerRepository matchPlayerRepository,
            LineupFiller lineupFiller
    ) {
        this.eventService = eventService;
        this.participantRepository = participantRepository;
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.lineupFiller = lineupFiller;
    }

    // All matches of the event, oldest first. Allowed for any event status.
    @Transactional(readOnly = true)
    public List<MatchResponse> list(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);

        return matchRepository.findByEventIdOrderByIdAsc(event.getId())
                .stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse get(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        return MatchResponse.from(findMatch(event, matchId));
    }

    // The "next up" list: active players who aren't on a court, fairest first.
    // Ties are shown alphabetically (no randomness here, so the list doesn't jump
    // around every time the screen refreshes). The real pick still breaks ties randomly.
    @Transactional(readOnly = true)
    public List<QueueEntryResponse> queue(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Map<Long, PlayerStats> stats = loadStats(event);
        Set<Long> busyIds = busyPlayers(event, null).keySet();

        // The participants arrive A to Z; List.sort is stable, so ties stay A to Z.
        List<EventParticipant> free = new ArrayList<>(activeParticipants(event).stream()
                .filter(participant -> !busyIds.contains(participant.getId()))
                .toList());
        free.sort(LineupFiller.fairestFirst(stats));

        return free.stream()
                .map(participant -> {
                    PlayerStats playerStats = stats.getOrDefault(participant.getId(), PlayerStats.NONE);
                    return new QueueEntryResponse(participant.getId(), participant.getPlayerName(),
                            playerStats.completedMatches(), playerStats.lastPlayedAt());
                })
                .toList();
    }

    // FR-014 / FR-018: set up the next match on a free court.
    @Transactional
    public MatchResponse create(String organizerEmail, Long eventId, CreateMatchRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);

        int court = request.courtNumber();
        if (court > event.getCourtCount()) {
            throw new IllegalArgumentException(
                    "Court number must be between 1 and " + event.getCourtCount() + ".");
        }

        // One unfinished match per court. (The database has a unique index for this too,
        // but checking here gives a clear message.)
        boolean courtBusy = matchRepository.findByEventIdAndStatusIn(event.getId(), OPEN_STATUSES)
                .stream()
                .anyMatch(match -> match.getCourtNumber() == court);
        if (courtBusy) {
            throw new InvalidStateException("Court " + court + " already has a match that hasn't finished.");
        }

        Lineup lineup = buildLineup(event, request.teamA(), request.teamB(), null);

        Match match = new Match(event, court);
        applyLineup(match, lineup);

        // save() inserts the match; cascade = ALL inserts its 4 seats too.
        return MatchResponse.from(matchRepository.save(match));
    }

    // "Plans changed": replace the line-up of a SCHEDULED match.
    // Seats left empty in the request are filled by the app again.
    @Transactional
    public MatchResponse updateLineup(String organizerEmail, Long eventId, Long matchId, LineupRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);
        Match match = findMatch(event, matchId);
        match.checkLineupCanChange();

        Lineup lineup = buildLineup(event, request.teamA(), request.teamB(), match);
        replaceLineup(match, lineup);
        return MatchResponse.from(match);
    }

    // Keep the organizer's choices, re-pick only the seats the app filled.
    @Transactional
    public MatchResponse reshuffle(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);
        Match match = findMatch(event, matchId);
        match.checkLineupCanChange();

        Lineup lineup = buildLineup(event, chosenByOrganizer(match, Team.A), chosenByOrganizer(match, Team.B), match);
        replaceLineup(match, lineup);
        return MatchResponse.from(match);
    }

    // SCHEDULED -> IN_PROGRESS: the line-up is locked.
    @Transactional
    public MatchResponse start(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);
        Match match = findMatch(event, matchId);

        // Dirty checking saves the change at commit; no save() needed.
        match.start();
        return MatchResponse.from(match);
    }

    // SCHEDULED or IN_PROGRESS -> CANCELLED. Frees the court and the players,
    // and never counts for fairness (FR-020, FR-035).
    @Transactional
    public MatchResponse cancel(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);
        Match match = findMatch(event, matchId);

        match.cancel();
        return MatchResponse.from(match);
    }

    // ---------- private helpers ----------

    private void requireActiveEvent(Event event) {
        if (!event.isActive()) {
            throw new InvalidStateException("Matches can only be changed while the event is active.");
        }
    }

    private Match findMatch(Event event, Long matchId) {
        return matchRepository.findByIdAndEventId(matchId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Match not found."));
    }

    private List<EventParticipant> activeParticipants(Event event) {
        return participantRepository.findByEventIdAndActiveTrueOrderByPlayerNameAsc(event.getId());
    }

    // participant id -> history, counting COMPLETED matches only (FR-020).
    private Map<Long, PlayerStats> loadStats(Event event) {
        return matchPlayerRepository.findStatsPerParticipant(event.getId(), MatchStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(
                        ParticipantMatchStats::getParticipantId,
                        row -> new PlayerStats(row.getMatchCount(), row.getLastPlayedAt())));
    }

    // Who is on a court right now: participant id -> court number.
    // `ignore` = a match whose players should NOT count as busy
    // (when changing a match's line-up, its own players are free to be re-picked).
    private Map<Long, Integer> busyPlayers(Event event, Match ignore) {
        Map<Long, Integer> busy = new HashMap<>();
        for (Match open : matchRepository.findByEventIdAndStatusIn(event.getId(), OPEN_STATUSES)) {
            if (open == ignore) {
                continue;
            }
            for (MatchPlayer player : open.getPlayers()) {
                busy.put(player.getParticipant().getId(), open.getCourtNumber());
            }
        }
        return busy;
    }

    // Checks the organizer's choices, then lets LineupFiller fill the empty seats.
    private Lineup buildLineup(Event event, List<Long> teamAIds, List<Long> teamBIds, Match ignore) {
        // A missing list in the JSON arrives as null: treat it as "no one chosen".
        List<Long> chosenA = teamAIds == null ? List.of() : teamAIds;
        List<Long> chosenB = teamBIds == null ? List.of() : teamBIds;

        // The same person can't take two seats (FR-017).
        Set<Long> allChosen = new HashSet<>(chosenA);
        allChosen.addAll(chosenB);
        if (allChosen.size() < chosenA.size() + chosenB.size()) {
            throw new IllegalArgumentException("A player can only take one seat in a match.");
        }

        // Function.identity() = "the participant itself" as the map's value.
        Map<Long, EventParticipant> active = activeParticipants(event).stream()
                .collect(Collectors.toMap(EventParticipant::getId, Function.identity()));
        Map<Long, Integer> busy = busyPlayers(event, ignore);

        List<EventParticipant> pinnedA = resolveChosen(chosenA, active, busy);
        List<EventParticipant> pinnedB = resolveChosen(chosenB, active, busy);

        // Everyone else who could fill an empty seat (FR-017, FR-019).
        List<EventParticipant> free = active.values().stream()
                .filter(participant -> !busy.containsKey(participant.getId()))
                .filter(participant -> !allChosen.contains(participant.getId()))
                .toList();

        return lineupFiller.fill(pinnedA, pinnedB, free, loadStats(event));
    }

    // Turns chosen ids into participants, checking each one may play right now.
    private List<EventParticipant> resolveChosen(
            List<Long> ids,
            Map<Long, EventParticipant> active,
            Map<Long, Integer> busy
    ) {
        List<EventParticipant> result = new ArrayList<>();
        for (Long id : ids) {
            EventParticipant participant = active.get(id);
            if (participant == null) {
                // Not in this event, or removed from it.
                throw new IllegalArgumentException("Participant " + id + " is not an active player in this event.");
            }
            if (busy.containsKey(id)) {
                throw new InvalidStateException("\"" + participant.getPlayerName()
                        + "\" is already in a match on court " + busy.get(id) + ".");
            }
            result.add(participant);
        }
        return result;
    }

    // The ids the organizer chose on one team (the seats reshuffle must keep).
    private List<Long> chosenByOrganizer(Match match, Team team) {
        return match.playersOn(team).stream()
                .filter(player -> !player.isAutoAssigned())
                .map(player -> player.getParticipant().getId())
                .toList();
    }

    // Copies the plain line-up into MatchPlayer rows.
    private void applyLineup(Match match, Lineup lineup) {
        for (Seat seat : lineup.teamA()) {
            match.addPlayer(seat.participant(), Team.A, seat.autoAssigned());
        }
        for (Seat seat : lineup.teamB()) {
            match.addPlayer(seat.participant(), Team.B, seat.autoAssigned());
        }
    }

    private void replaceLineup(Match match, Lineup lineup) {
        match.clearPlayers();
        // flush() = "send the pending SQL to the database now".
        // Hibernate normally runs INSERTs before DELETEs. If the same player is
        // re-picked, the new seat would be inserted while the old one still exists,
        // breaking UNIQUE (match_id, participant_id). So: delete first, then insert.
        matchRepository.flush();
        applyLineup(match, lineup);
    }
}