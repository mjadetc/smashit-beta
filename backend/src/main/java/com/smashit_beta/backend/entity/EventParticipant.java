package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

// A person playing in one event (table created by V5__create_event_participants_table.sql).
// Participants are just names: they don't need an account (FR-013).
@Entity
@Table(name = "event_participants")
public class EventParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many participants belong to one event (FR-012).
    // LAZY: don't load the Event unless we actually use it.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "player_name", nullable = false, length = 80)
    private String playerName;

    // false = removed from the event ("soft delete"). The row stays so that
    // match history and expenses that mention this person remain intact.
    @Column(name = "active", nullable = false)
    private boolean active;

    // NEW (V11): does this person pay an equal share of the expenses?
    // null = the default rule (yes if active or they finished a match);
    // true / false = the organizer decided. "Boolean" (not "boolean") so it can be null.
    @Column(name = "shares_equally")
    private Boolean sharesEqually;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA.
    protected EventParticipant() {
    }

    public EventParticipant(Event event, String playerName) {
        this.event = event;
        this.playerName = playerName;
        this.active = true;
    }

    // FR-011: update a participant's name.
    public void rename(String playerName) {
        this.playerName = playerName;
    }

    // FR-011: remove from the event (soft delete).
    public void remove() {
        this.active = false;
    }

    // Add a previously removed participant back, keeping their old history.
    public void restore() {
        this.active = true;
    }

    // NEW (V11): the organizer's choice; null goes back to the default rule.
    public void changeSharesEqually(Boolean sharesEqually) {
        this.sharesEqually = sharesEqually;
    }

    public Boolean getSharesEqually() {
        return sharesEqually;
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

    public String getPlayerName() {
        return playerName;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}