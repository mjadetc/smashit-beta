package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

// One seat in one match: who sits there, on which side of the net,
// and whether the organizer or the app chose them (table match_players).
//
// There is no long-lived "Team" entity on purpose: a partnership only exists
// for one match. Next match, a player can partner with someone else, and nothing
// needs to be undone.
@Entity
@Table(name = "match_players")
public class MatchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The match this seat belongs to. "Many players in one match."
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    // Who is playing. "One participant appears in many matches over the event."
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private EventParticipant participant;

    // Stored as 'A' or 'B' in a CHAR(1) column.
    @Enumerated(EnumType.STRING)
    @Column(name = "team", nullable = false, length = 1)
    private Team team;

    // true  = the app filled this seat (it may be replaced by "reshuffle")
    // false = the organizer chose this player (reshuffle keeps them)
    @Column(name = "auto_assigned", nullable = false)
    private boolean autoAssigned;

    protected MatchPlayer() {
    }

    // Package-private (no "public"): only Match.addPlayer() creates these,
    // so a seat can never exist without being linked to its match.
    MatchPlayer(Match match, EventParticipant participant, Team team, boolean autoAssigned) {
        this.match = match;
        this.participant = participant;
        this.team = team;
        this.autoAssigned = autoAssigned;
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public EventParticipant getParticipant() {
        return participant;
    }

    public Team getTeam() {
        return team;
    }

    public boolean isAutoAssigned() {
        return autoAssigned;
    }
}