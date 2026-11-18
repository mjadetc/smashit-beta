package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.PointRequest;
import com.smashit_beta.backend.dto.request.StartGameRequest;
import com.smashit_beta.backend.dto.response.GameSummaryResponse;
import com.smashit_beta.backend.dto.response.LiveGameResponse;
import com.smashit_beta.backend.dto.response.NextStep;
import com.smashit_beta.backend.dto.response.PlayerRef;
import com.smashit_beta.backend.dto.response.ScoreboardResponse;
import com.smashit_beta.backend.dto.response.SeatResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchGame;
import com.smashit_beta.backend.entity.MatchPlayer;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.entity.Team;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.MatchGameRepository;
import com.smashit_beta.backend.repository.MatchRepository;
import com.smashit_beta.backend.service.BadmintonRules.GameSetup;
import com.smashit_beta.backend.service.BadmintonRules.GameState;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Scoring a match on the court screen (FR-026 to FR-034):
//
//   match IN_PROGRESS -> start game (pick server + receiver) -> record points ... -> game over
//   -> (best of three) start the next game ...  -> a team has won enough games
//   -> complete match (the organizer confirms) -> COMPLETED, counts for matchmaking
//
// "Undo" removes the latest rally at any point before the match is completed,
// even the one that ended a game.
//
// Every method returns the full scoreboard, so the frontend can redraw after each tap.
@Service
public class ScoringService {

    private final EventService eventService;
    private final MatchRepository matchRepository;
    private final MatchGameRepository gameRepository;

    public ScoringService(EventService eventService, MatchRepository matchRepository,
                          MatchGameRepository gameRepository) {
        this.eventService = eventService;
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
    }

    // FR-034: the scoreboard / result of one match. Works for any match status.
    @Transactional(readOnly = true)
    public ScoreboardResponse scoreboard(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Match match = findMatch(event, matchId);
        return buildScoreboard(event, match, games(match));
    }

    // FR-026: start the next game with the chosen server and receiver.
    @Transactional
    public ScoreboardResponse startGame(String organizerEmail, Long eventId, Long matchId,
                                        StartGameRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Match match = findScorableMatch(event, matchId);
        List<MatchGame> games = games(match);

        if (games.stream().anyMatch(MatchGame::isInProgress)) {
            throw new InvalidStateException("Game " + games.get(games.size() - 1).getGameNumber()
                    + " is still being played.");
        }
        if (matchWinner(event, games) != null) {
            throw new InvalidStateException("This match already has a winner. Complete the match instead.");
        }

        int gameNumber = games.size() + 1;

        // From game 2 on, the winner of the previous game serves first (BWF rules).
        if (gameNumber > 1) {
            Team previousWinner = games.get(games.size() - 1).getWinningTeam();
            if (request.servingTeam() != previousWinner) {
                throw new IllegalArgumentException("Team " + previousWinner + " won game " + (gameNumber - 1)
                        + ", so team " + previousWinner + " serves first in game " + gameNumber + ".");
            }
        }

        Team receivingTeam = opponentOf(request.servingTeam());
        EventParticipant server = seatedPlayer(match, request.servingTeam(), request.serverId(),
                "The server must be a player on team " + request.servingTeam() + ".");
        EventParticipant receiver = seatedPlayer(match, receivingTeam, request.receiverId(),
                "The receiver must be a player on team " + receivingTeam + ".");

        MatchGame game = gameRepository.save(
                new MatchGame(match, gameNumber, request.servingTeam(), server, receiver));
        games.add(game);

        return buildScoreboard(event, match, games);
    }

    // FR-029 / FR-030 / FR-031: one rally won by the given team.
    @Transactional
    public ScoreboardResponse recordPoint(String organizerEmail, Long eventId, Long matchId, PointRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Match match = findScorableMatch(event, matchId);
        List<MatchGame> games = games(match);

        MatchGame game = games.stream()
                .filter(MatchGame::isInProgress)
                .findFirst()
                .orElseThrow(() -> new InvalidStateException("There is no game in progress. Start a game first."));

        GameSetup setup = setupOf(event, match, game);

        // Who serves and receives THIS rally = the state before it.
        GameState before = BadmintonRules.replay(setup, game.rallyWinners());

        // The state after it: replay again with the new rally added at the end.
        List<Team> rallies = new ArrayList<>(game.rallyWinners());
        rallies.add(request.team());
        GameState after = BadmintonRules.replay(setup, rallies);

        Map<Long, EventParticipant> seated = seatedById(match);
        game.addPoint(request.team(), seated.get(before.serverId()), seated.get(before.receiverId()),
                after.teamAScore(), after.teamBScore());
        game.updateResult(after.teamAScore(), after.teamBScore(), after.winner());

        // No save() needed: the game is "managed", so Hibernate saves the change
        // (and, through cascade, the new point) when the transaction commits.
        return buildScoreboard(event, match, games);
    }

    // Undo the latest rally. If the latest game has no rallies yet, undo "start game" instead
    // (e.g. the wrong server was picked). Not allowed once the match is completed.
    @Transactional
    public ScoreboardResponse undo(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Match match = findScorableMatch(event, matchId);
        List<MatchGame> games = games(match);

        if (games.isEmpty()) {
            throw new InvalidStateException("There is nothing to undo.");
        }

        MatchGame latest = games.get(games.size() - 1);
        if (latest.getPoints().isEmpty()) {
            gameRepository.delete(latest);
            games.remove(latest);
        } else {
            latest.removeLastPoint();
            // Replay what's left: this also reopens the game if the removed rally had ended it.
            GameState state = BadmintonRules.replay(setupOf(event, match, latest), latest.rallyWinners());
            latest.updateResult(state.teamAScore(), state.teamBScore(), state.winner());
        }

        return buildScoreboard(event, match, games);
    }

    // FR-032 / FR-033: the organizer confirms the result. The match becomes COMPLETED,
    // its court and players are free again, and it now counts for matchmaking.
    @Transactional
    public ScoreboardResponse completeMatch(String organizerEmail, Long eventId, Long matchId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Match match = findScorableMatch(event, matchId);
        List<MatchGame> games = games(match);

        Team winner = matchWinner(event, games);
        if (winner == null) {
            throw new InvalidStateException("The match doesn't have a winner yet.");
        }

        match.complete(winner);
        return buildScoreboard(event, match, games);
    }

    // ---------- private helpers ----------

    private Match findMatch(Event event, Long matchId) {
        return matchRepository.findByIdAndEventId(matchId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Match not found."));
    }

    // A match whose score may change: the event is active and the match has started.
    private Match findScorableMatch(Event event, Long matchId) {
        if (!event.isActive()) {
            throw new InvalidStateException("Matches can only be changed while the event is active.");
        }
        Match match = findMatch(event, matchId);
        if (match.getStatus() == MatchStatus.SCHEDULED) {
            throw new InvalidStateException("Start the match before scoring it.");
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new InvalidStateException("A completed or cancelled match can't be scored.");
        }
        return match;
    }

    // A modifiable copy of the match's games, game 1 first.
    private List<MatchGame> games(Match match) {
        return new ArrayList<>(gameRepository.findByMatchIdOrderByGameNumberAsc(match.getId()));
    }

    // The winner of the match so far, or null (in-progress games count for nobody).
    private Team matchWinner(Event event, List<MatchGame> games) {
        List<Team> gameWinners = games.stream().map(MatchGame::getWinningTeam).toList();
        return BadmintonRules.matchWinner(event.getMatchFormat(), gameWinners);
    }

    // The participant with this id, but only if they sit on that team in this match.
    private EventParticipant seatedPlayer(Match match, Team team, Long participantId, String errorMessage) {
        return match.playersOn(team).stream()
                .map(MatchPlayer::getParticipant)
                .filter(participant -> participant.getId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    // The partner of `player` on the same team.
    private Long partnerOf(Match match, Team team, Long playerId) {
        return match.playersOn(team).stream()
                .map(seat -> seat.getParticipant().getId())
                .filter(id -> !id.equals(playerId))
                .findFirst()
                .orElseThrow();
    }

    // The 4 seated players by id, to turn the ids in a GameState back into people.
    private Map<Long, EventParticipant> seatedById(Match match) {
        return match.getPlayers().stream()
                .map(MatchPlayer::getParticipant)
                .collect(Collectors.toMap(EventParticipant::getId, participant -> participant));
    }

    // The starting state of a game, in the plain form BadmintonRules understands.
    private GameSetup setupOf(Event event, Match match, MatchGame game) {
        Team serving = game.getInitialServingTeam();
        Long serverId = game.getInitialServer().getId();
        Long receiverId = game.getInitialReceiver().getId();
        return new GameSetup(
                event.getMatchFormat(),
                event.getChangeEndsAt(),
                game.getGameNumber(),
                serving,
                serverId,
                partnerOf(match, serving, serverId),
                receiverId,
                partnerOf(match, opponentOf(serving), receiverId));
    }

    private static Team opponentOf(Team team) {
        return team == Team.A ? Team.B : Team.A;
    }

    private ScoreboardResponse buildScoreboard(Event event, Match match, List<MatchGame> games) {
        Team winner = matchWinner(event, games);
        MatchGame latest = games.isEmpty() ? null : games.get(games.size() - 1);

        LiveGameResponse currentGame = null;
        if (latest != null) {
            GameState state = BadmintonRules.replay(setupOf(event, match, latest), latest.rallyWinners());
            currentGame = toLiveGame(latest, state, seatedById(match));
        }

        // FR-033: duration = completed - started.
        Long durationSeconds = match.getCompletedAt() == null || match.getStartedAt() == null
                ? null
                : Duration.between(match.getStartedAt(), match.getCompletedAt()).toSeconds();

        return new ScoreboardResponse(
                match.getId(),
                event.getId(),
                match.getCourtNumber(),
                event.getMatchFormat(),
                match.getStatus(),
                match.playersOn(Team.A).stream().map(SeatResponse::from).toList(),
                match.playersOn(Team.B).stream().map(SeatResponse::from).toList(),
                games.stream().map(GameSummaryResponse::from).toList(),
                currentGame,
                winner,
                nextStep(match, latest, winner),
                match.getStartedAt(),
                match.getCompletedAt(),
                durationSeconds);
    }

    private LiveGameResponse toLiveGame(MatchGame game, GameState state, Map<Long, EventParticipant> seated) {
        return new LiveGameResponse(
                game.getGameNumber(),
                state.teamAScore(),
                state.teamBScore(),
                game.getStatus(),
                state.winner(),
                state.servingTeam(),
                PlayerRef.from(seated.get(state.serverId())),
                PlayerRef.from(seated.get(state.receiverId())),
                PlayerRef.from(seated.get(state.teamARightCourtId())),
                PlayerRef.from(seated.get(state.teamALeftCourtId())),
                PlayerRef.from(seated.get(state.teamBRightCourtId())),
                PlayerRef.from(seated.get(state.teamBLeftCourtId())),
                state.endsChanged(),
                state.changeEndsNow(),
                game.getPoints().size());
    }

    private NextStep nextStep(Match match, MatchGame latest, Team winner) {
        // "switch" on an enum: one case per value.
        return switch (match.getStatus()) {
            case SCHEDULED -> NextStep.START_MATCH;
            case COMPLETED, CANCELLED -> NextStep.NONE;
            case IN_PROGRESS -> {
                if (winner != null) {
                    yield NextStep.COMPLETE_MATCH;
                }
                // "yield" = the value of this case block.
                yield latest != null && latest.isInProgress() ? NextStep.RECORD_POINT : NextStep.START_GAME;
            }
        };
    }
}