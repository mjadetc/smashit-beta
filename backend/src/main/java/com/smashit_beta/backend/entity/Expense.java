package com.smashit_beta.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// One thing someone paid for, for the whole group (table expenses, FR-039 / FR-040).
// Each participant's share is NOT stored here: it's calculated from all the
// event's expenses whenever the summary is asked for (FR-041).
//
// CHANGED (V11): the payer is EITHER a participant (paidBy) OR an outside person
// (outsidePayerName, e.g. a non-playing organizer). An outside payer is either paid
// back (outsidePayerRepaid = true) or gave it as a gift (false).
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // The participant who paid upfront, or null when an outside person paid.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_id")
    private EventParticipant paidBy;

    // NEW (V11): only for outside payers.
    @Column(name = "outside_payer_name", length = 80)
    private String outsidePayerName;

    // NEW (V11): only for outside payers. Boolean (not boolean) so it can be null.
    @Column(name = "outside_payer_repaid")
    private Boolean outsidePayerRepaid;

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

    public Expense(Event event, String description, ExpenseCategory category,
                   BigDecimal amount, LocalDate expenseDate) {
        this.event = event;
        updateDetails(description, category, amount, expenseDate);
    }

    // The details that don't depend on who paid. The event can't change.
    public void updateDetails(String description, ExpenseCategory category,
                              BigDecimal amount, LocalDate expenseDate) {
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.expenseDate = expenseDate;
    }

    // A participant paid. Clears any outside payer (chk_expenses_one_payer in V11).
    public void paidByParticipant(EventParticipant participant) {
        this.paidBy = participant;
        this.outsidePayerName = null;
        this.outsidePayerRepaid = null;
    }

    // Someone outside the event paid. repaid = false means it was a gift.
    public void paidByOutsider(String name, boolean repaid) {
        this.paidBy = null;
        this.outsidePayerName = name;
        this.outsidePayerRepaid = repaid;
    }

    public boolean isPaidByOutsider() {
        return paidBy == null;
    }

    // A gift from an outside payer: nobody pays it back, so it comes off the total.
    public boolean isGift() {
        return isPaidByOutsider() && !outsidePayerRepaid;
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

    public String getOutsidePayerName() {
        return outsidePayerName;
    }

    public Boolean getOutsidePayerRepaid() {
        return outsidePayerRepaid;
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