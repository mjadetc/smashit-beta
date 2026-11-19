package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.response.CourtSummaryResponse;
import com.smashit_beta.backend.dto.response.EventSummaryResponse;
import com.smashit_beta.backend.dto.response.GameSummaryResponse;
import com.smashit_beta.backend.dto.response.MatchResultResponse;
import com.smashit_beta.backend.dto.response.PlayerRef;
import com.smashit_beta.backend.dto.response.PlayerStatsResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchGame;
import com.smashit_beta.backend.entity.MatchPlayer;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.entity.Team;
import com.smashit_beta.backend.repository.EventParticipantRepository;
import com.smashit_beta.backend.repository.MatchGameRepository;
import com.smashit_beta.backend.repository.MatchRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Event statistics (FR-036 to FR-038).
//
// Nothing here is STORED: every number is counted from the completed matches each
// time it's asked for. So FR-037 ("update statistics after a match is completed")
// happens by itself: the moment a match becomes COMPLETED, the next request counts it,
// and an undo before completion can never leave a wrong number behind.
// (A beta event has at most a few hundred matches, so counting in Java is fast enough.)
@Service
public class StatsService {

    private final EventService eventService;
    private final EventParticipantRepository participantRepository;
    private final MatchRepository matchRepository;
    private final MatchGameRepository gameRepository;

    public StatsService(EventService eventService, EventParticipantRepository participantRepository,
                        MatchRepository matchRepository, MatchGameRepository gameRepository) {
        this.eventService = eventService;
        this.participantRepository = participantRepository;
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
    }

    // FR-036: the standings. Every active player is listed (even with 0 matches);
    // a removed player is listed only if they finished at least one match.
    // Order: most wins first, then fewest losses, then A to Z.
    @Transactional(readOnly = true)
    public List<PlayerStatsResponse> playerStats(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        List<Match> completed = completedMatches(event);
        Map<Long, List<MatchGame>> gamesByMatch = completedGamesByMatch(event);

        // participant id -> their running totals
        Map<Long, Tally> tallies = new HashMap<>();
        for (Match match : completed) {
            List<MatchGame> games = gamesByMatch.getOrDefault(match.getId(), List.of());
            for (MatchPlayer seat : match.getPlayers()) {
                tallies.computeIfAbsent(seat.getParticipant().getId(), id -> new Tally())
                        .addMatch(seat.getTeam(), match.getWinningTeam(), games);
            }
        }

        return participantRepository.findByEventIdOrderByPlayerNameAsc(event.getId())
                .stream()
                .filter(participant -> participant.isActive() || tallies.containsKey(participant.getId()))
                .map(participant -> toResponse(participant, tallies.getOrDefault(participant.getId(), new Tally())))
                .sorted(Comparator.comparingLong(PlayerStatsResponse::wins).reversed()
                        .thenComparingLong(PlayerStatsResponse::losses))
                // (sorted() is stable, so equal players stay in A-to-Z order.)
                .toList();
    }

    // FR-038: the event summary.
    @Transactional(readOnly = true)
    public EventSummaryResponse summary(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        List<Match> matches = matchRepository.findByEventIdOrderByIdAsc(event.getId());
        Map<Long, List<MatchGame>> gamesByMatch = completedGamesByMatch(event);

        List<Match> completed = matches.stream()
                .filter(match -> match.getStatus() == MatchStatus.COMPLETED)
                .toList();
        List<MatchGame> completedGames = gamesByMatch.values().stream().flatMap(List::stream).toList();

        // Durations of the completed matches, in seconds.
        List<Long> durations = completed.stream().map(StatsService::durationSeconds).toList();
        Long average = durations.isEmpty()
                ? null
                : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));
        Long longest = durations.stream().max(Long::compare).orElse(null);

        // Most recent first.
        List<MatchResultResponse> results = completed.stream()
                .sorted(Comparator.comparing(Match::getCompletedAt).reversed())
                .map(match -> toResult(match, gamesByMatch.getOrDefault(match.getId(), List.of())))
                .toList();

        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                event.getStatus(),
                event.getMatchFormat(),
                participantRepository.findByEventIdAndActiveTrueOrderByPlayerNameAsc(event.getId()).size(),
                countWithStatus(matches, MatchStatus.SCHEDULED),
                countWithStatus(matches, MatchStatus.IN_PROGRESS),
                completed.size(),
                countWithStatus(matches, MatchStatus.CANCELLED),
                completedGames.size(),
                completedGames.stream().mapToLong(game -> game.getTeamAScore() + game.getTeamBScore()).sum(),
                average,
                longest,
                courtSummaries(event, matches),
                results);
    }

    // ---------- private helpers ----------

    private List<Match> completedMatches(Event event) {
        return matchRepository.findByEventIdAndStatusIn(event.getId(), List.of(MatchStatus.COMPLETED));
    }

    // match id -> its games (game 1 first), for completed matches only.
    // groupingBy keeps the order of the list it reads, which is already by game number.
    private Map<Long, List<MatchGame>> completedGamesByMatch(Event event) {
        return gameRepository.findByMatchEventIdAndMatchStatusOrderByGameNumberAsc(event.getId(), MatchStatus.COMPLETED)
                .stream()
                .collect(Collectors.groupingBy(game -> game.getMatch().getId()));
    }

    // One line per court, 1 to courtCount.
    private List<CourtSummaryResponse> courtSummaries(Event event, List<Match> matches) {
        return IntStream.rangeClosed(1, event.getCourtCount())
                .mapToObj(court -> {
                    long finished = matches.stream()
                            .filter(match -> match.getCourtNumber() == court)
                            .filter(match -> match.getStatus() == MatchStatus.COMPLETED)
                            .count();
                    // There's at most one unfinished match per court (V10 unique index).
                    Match current = matches.stream()
                            .filter(match -> match.getCourtNumber() == court && match.isOpen())
                            .findFirst()
                            .orElse(null);
                    return new CourtSummaryResponse(court, finished,
                            current == null ? null : current.getId(),
                            current == null ? null : current.getStatus());
                })
                .toList();
    }

    private MatchResultResponse toResult(Match match, List<MatchGame> games) {
        return new MatchResultResponse(
                match.getId(),
                match.getCourtNumber(),
                playersOn(match, Team.A),
                playersOn(match, Team.B),
                match.getWinningTeam(),
                games.stream().map(GameSummaryResponse::from).toList(),
                durationSeconds(match),
                match.getCompletedAt());
    }

    private List<PlayerRef> playersOn(Match match, Team team) {
        return match.playersOn(team).stream()
                .map(seat -> PlayerRef.from(seat.getParticipant()))
                .toList();
    }

    private static long countWithStatus(List<Match> matches, MatchStatus status) {
        return matches.stream().filter(match -> match.getStatus() == status).count();
    }

    // FR-033: completed - started. A completed match always has both times (V6 check).
    private static Long durationSeconds(Match match) {
        return Duration.between(match.getStartedAt(), match.getCompletedAt()).toSeconds();
    }

    private static PlayerStatsResponse toResponse(EventParticipant participant, Tally tally) {
        return new PlayerStatsResponse(participant.getId(), participant.getPlayerName(), participant.isActive(),
                tally.matches, tally.wins, tally.losses, tally.gamesWon, tally.gamesLost,
                tally.pointsWon, tally.pointsLost);
    }

    // One player's running totals while we go through the matches.
    // A small private class with plain fields: it never leaves this service.
    private static class Tally {
        long matches;
        long wins;
        long losses;
        long gamesWon;
        long gamesLost;
        long pointsWon;
        long pointsLost;

        // Adds one completed match, seen from `team`'s side of the net.
        void addMatch(Team team, Team matchWinner, List<MatchGame> games) {
            matches++;
            if (team == matchWinner) {
                wins++;
            } else {
                losses++;
            }
            for (MatchGame game : games) {
                if (game.getWinningTeam() == team) {
                    gamesWon++;
                } else {
                    gamesLost++;
                }
                int own = team == Team.A ? game.getTeamAScore() : game.getTeamBScore();
                int other = team == Team.A ? game.getTeamBScore() : game.getTeamAScore();
                pointsWon += own;
                pointsLost += other;
            }
        }
    }
}