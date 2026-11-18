package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// One game inside a match (table match_games): game 1 for a single-game match,
// games 1 to 3 for best-of-three.
//
// It stores only the STARTING serving state (FR-026) and the list of rallies.
// Everything else (who serves now, who stands in which service court, when to
// change ends) is calculated by BadmintonRules from those two things.
@Entity
@Table(name = "match_games")
public class MatchGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "game_number", nullable = false)
    private int gameNumber;

    // A quick-to-read copy of the latest score (0-0 before the first rally).
    @Column(name = "team_a_score", nullable = false)
    private int teamAScore;

    @Column(name = "team_b_score", nullable = false)
    private int teamBScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameStatus status;

    // NULL while the game is in progress.
    @Enumerated(EnumType.STRING)
    @Column(name = "winning_team", length = 1)
    private Team winningTeam;

    // FR-026: who serves first, and who receives that first serve.
    // At 0-0 both of them stand in their team's RIGHT service court.
    @Enumerated(EnumType.STRING)
    @Column(name = "initial_serving_team", nullable = false, length = 1)
    private Team initialServingTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initial_server_id", nullable = false)
    private EventParticipant initialServer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initial_receiver_id", nullable = false)
    private EventParticipant initialReceiver;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Every rally, oldest first. @OrderBy sorts the list when Hibernate loads it.
    // cascade + orphanRemoval: adding to / removing from this list inserts / deletes rows.
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<GamePoint> points = new ArrayList<>();

    protected MatchGame() {
    }

    public MatchGame(Match match, int gameNumber, Team initialServingTeam,
                     EventParticipant initialServer, EventParticipant initialReceiver) {
        this.match = match;
        this.gameNumber = gameNumber;
        this.initialServingTeam = initialServingTeam;
        this.initialServer = initialServer;
        this.initialReceiver = initialReceiver;
        this.status = GameStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    // Records one rally. server/receiver = who served and received it;
    // teamAScore/teamBScore = the score after it.
    public void addPoint(Team scoringTeam, EventParticipant server, EventParticipant receiver,
                         int teamAScore, int teamBScore) {
        points.add(new GamePoint(this, points.size() + 1, scoringTeam, server, receiver, teamAScore, teamBScore));
    }

    // "Undo": removes the latest rally (orphanRemoval deletes its row).
    public void removeLastPoint() {
        points.remove(points.size() - 1);
    }

    // Stores the result after a change. winner = null means "still being played",
    // so undoing the winning rally reopens the game.
    public void updateResult(int teamAScore, int teamBScore, Team winner) {
        this.teamAScore = teamAScore;
        this.teamBScore = teamBScore;
        this.winningTeam = winner;
        this.status = winner == null ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
        this.completedAt = winner == null ? null : Instant.now();
    }

    public boolean isInProgress() {
        return status == GameStatus.IN_PROGRESS;
    }

    // The winner of each rally, in order. This is what BadmintonRules replays.
    public List<Team> rallyWinners() {
        return points.stream().map(GamePoint::getScoringTeam).toList();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    public int getTeamAScore() {
        return teamAScore;
    }

    public int getTeamBScore() {
        return teamBScore;
    }

    public GameStatus getStatus() {
        return status;
    }

    public Team getWinningTeam() {
        return winningTeam;
    }

    public Team getInitialServingTeam() {
        return initialServingTeam;
    }

    public EventParticipant getInitialServer() {
        return initialServer;
    }

    public EventParticipant getInitialReceiver() {
        return initialReceiver;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<GamePoint> getPoints() {
        return points;
    }
}