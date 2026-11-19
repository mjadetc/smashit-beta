package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// One thing someone paid for, for the whole group (table expenses, FR-039 / FR-040).
// Each participant's share is NOT stored here: it's calculated from all the
// event's expenses whenever the summary is asked for (FR-041).
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // The participant who paid upfront.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id", nullable = false)
    private EventParticipant paidBy;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ExpenseCategory category;

    // BigDecimal = an exact decimal number. Money must never be a double:
    // 0.1 + 0.2 is 0.30000000000000004 as a double, but exactly 0.30 as a BigDecimal.
    // precision 10, scale 2 = NUMERIC(10, 2) in V8.
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // LocalDate = just a date, no time (DATE column), e.g. 2026-11-20.
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Expense() {
    }

    public Expense(Event event, EventParticipant paidBy, String description, ExpenseCategory category,
                   BigDecimal amount, LocalDate expenseDate) {
        this.event = event;
        update(paidBy, description, category, amount, expenseDate);
    }

    // Changes every editable detail at once (like Event.updateDetails). The event can't change.
    public void update(EventParticipant paidBy, String description, ExpenseCategory category,
                       BigDecimal amount, LocalDate expenseDate) {
        this.paidBy = paidBy;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.expenseDate = expenseDate;
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

    public EventParticipant getPaidBy() {
        return paidBy;
    }

    public String getDescription() {
        return description;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}