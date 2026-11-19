package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.Expense;
import com.smashit_beta.backend.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// One expense as the frontend sees it.
// CHANGED (V11): paidBy is null when someone outside the event paid; then
// outsidePayerName and outsidePayerRepaid say who, and whether they get paid back.
public record ExpenseResponse(
        Long id,
        Long eventId,
        String description,
        ExpenseCategory category,
        BigDecimal amount,
        LocalDate expenseDate,
        PlayerRef paidBy,
        String outsidePayerName,
        Boolean outsidePayerRepaid,
        Instant createdAt
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getEvent().getId(),
                expense.getDescription(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getPaidBy() == null ? null : PlayerRef.from(expense.getPaidBy()),
                expense.getOutsidePayerName(),
                expense.getOutsidePayerRepaid(),
                expense.getCreatedAt());
    }
}