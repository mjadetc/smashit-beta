package com.smashit_beta.backend.entity;

import com.smashit_beta.backend.exception.InvalidStateException;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// One doubles match on one court (table "matches").
// Its life:  SCHEDULED --start--> IN_PROGRESS --(scoring, next branch)--> COMPLETED
//            SCHEDULED or IN_PROGRESS --cancel--> CANCELLED
// While SCHEDULED, the organizer can still change or reshuffle the line-up.
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "Many matches belong to one event."
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // 1 to event.courtCount.
    @Column(name = "court_number", nullable = false)
    private int courtNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MatchStatus status;

    // NULL until the match is completed (set by the scoring feature).
    @Enumerated(EnumType.STRING)
    @Column(name = "winning_team", length = 1)
    private Team winningTeam;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // The 4 seats. @OneToMany is the "other side" of MatchPlayer.match:
    // mappedBy = "match" says the foreign key lives in MatchPlayer, not here.
    // cascade = ALL        -> saving a Match also saves its seats.
    // orphanRemoval = true -> a seat removed from this list is deleted from the DB.
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchPlayer> players = new ArrayList<>();

    protected Match() {
    }

    public Match(Event event, int courtNumber) {
        this.event = event;
        this.courtNumber = courtNumber;
        this.status = MatchStatus.SCHEDULED;
    }

    // Puts a participant in a seat on team A or B. Keeps both sides of the
    // relationship in sync (this list AND the match field in MatchPlayer).
    public void addPlayer(EventParticipant participant, Team team, boolean autoAssigned) {
        players.add(new MatchPlayer(this, participant, team, autoAssigned));
    }

    // Empties all seats so a new line-up can be put in (change line-up / reshuffle).
    // orphanRemoval makes Hibernate delete the old rows.
    public void clearPlayers() {
        checkLineupCanChange();
        players.clear();
    }

    // Throws unless the line-up may still change (only before the match starts).
    // Public so the service can check this BEFORE doing any other work.
    public void checkLineupCanChange() {
        requireScheduled("The line-up can only be changed before the match starts.");
    }

    // SCHEDULED -> IN_PROGRESS: players are on court, the line-up is locked.
    public void start() {
        requireScheduled("Only a scheduled match can be started.");
        this.status = MatchStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    // SCHEDULED or IN_PROGRESS -> CANCELLED. A cancelled match never counts (FR-035)
    // and frees the court and its players straight away.
    public void cancel() {
        if (!isOpen()) {
            throw new InvalidStateException("Only a scheduled or in-progress match can be cancelled.");
        }
        this.status = MatchStatus.CANCELLED;
    }

    // "Open" = not finished yet: it occupies its court and its 4 players.
    public boolean isOpen() {
        return status == MatchStatus.SCHEDULED || status == MatchStatus.IN_PROGRESS;
    }

    // The seats on one side, e.g. playersOn(Team.A).
    public List<MatchPlayer> playersOn(Team team) {
        return players.stream()
                .filter(player -> player.getTeam() == team)
                .toList();
    }

    private void requireScheduled(String message) {
        if (status != MatchStatus.SCHEDULED) {
            throw new InvalidStateException(message);
        }
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

    public Event getEvent() {
        return event;
    }

    public int getCourtNumber() {
        return courtNumber;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Team getWinningTeam() {
        return winningTeam;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<MatchPlayer> getPlayers() {
        return players;
    }
}