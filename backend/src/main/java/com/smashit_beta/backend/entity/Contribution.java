package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

// NEW (V11): money a participant WILLINGLY puts in, e.g. "Ana: 200 for the shuttlecocks".
// Contributions come off the total BEFORE the rest is split equally, so a generous
// player lowers everyone else's share. (Whether Ana ALSO pays an equal share is her
// "shares equally" setting on EventParticipant, not part of the contribution.)
@Entity
@Table(name = "contributions")
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private EventParticipant participant;

    // Optional label: which expense it was "for". It doesn't change the maths:
    // the amount always reduces the whole pool.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contribution() {
    }

    public Contribution(Event event, EventParticipant participant, Expense expense, BigDecimal amount, String note) {
        this.event = event;
        update(participant, expense, amount, note);
    }

    public void update(EventParticipant participant, Expense expense, BigDecimal amount, String note) {
        this.participant = participant;
        this.expense = expense;
        this.amount = amount;
        this.note = note;
    }

    // Removes the "for this expense" label, e.g. because that expense is being deleted.
    public void unlinkExpense() {
        this.expense = null;
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

    public EventParticipant getParticipant() {
        return participant;
    }

    public Expense getExpense() {
        return expense;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}