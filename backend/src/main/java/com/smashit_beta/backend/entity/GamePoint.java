package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

// One rally in one game (table game_points): who won it, who served and received,
// and the score AFTER it. Rows are only added, or the latest one removed by "undo".
@Entity
@Table(name = "game_points")
public class GamePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private MatchGame game;

    // 1, 2, 3, ... in the order the rallies were played.
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    // The team that won the rally.
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_team", nullable = false, length = 1)
    private Team scoringTeam;

    // A snapshot of who served and who received THIS rally, for match history.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private EventParticipant server;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private EventParticipant receiver;

    // The score after this rally.
    @Column(name = "team_a_score", nullable = false)
    private int teamAScore;

    @Column(name = "team_b_score", nullable = false)
    private int teamBScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GamePoint() {
    }

    // Package-private: only MatchGame.addPoint() creates these.
    GamePoint(MatchGame game, int sequenceNumber, Team scoringTeam,
              EventParticipant server, EventParticipant receiver, int teamAScore, int teamBScore) {
        this.game = game;
        this.sequenceNumber = sequenceNumber;
        this.scoringTeam = scoringTeam;
        this.server = server;
        this.receiver = receiver;
        this.teamAScore = teamAScore;
        this.teamBScore = teamBScore;
    }

    // No @PreUpdate: a point is never edited after it's saved.
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public Team getScoringTeam() {
        return scoringTeam;
    }

    public EventParticipant getServer() {
        return server;
    }

    public EventParticipant getReceiver() {
        return receiver;
    }

    public int getTeamAScore() {
        return teamAScore;
    }

    public int getTeamBScore() {
        return teamBScore;
    }
}