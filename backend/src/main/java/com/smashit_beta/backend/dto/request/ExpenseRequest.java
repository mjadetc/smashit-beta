package com.smashit_beta.backend.dto.request;

import com.smashit_beta.backend.entity.ExpenseCategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// FR-040: the JSON body for recording or editing an expense, e.g.
// { "description": "Court 1 and 2, 3 hours", "category": "COURT_RENTAL",
//   "amount": 1500.00, "expenseDate": "2026-11-20", "paidById": 12 }
public record ExpenseRequest(

        @NotBlank(message = "Description is required.")
        @Size(max = 255, message = "Description must be at most 255 characters.")
        String description,

        @NotNull(message = "Category is required.")
        ExpenseCategory category,

        // inclusive = true: 0.01 itself is allowed.
        // @Digits matches NUMERIC(10, 2): up to 8 digits before the point, 2 after.
        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01.")
        @Digits(integer = 8, fraction = 2, message = "Amount can have at most 8 digits and 2 decimal places.")
        BigDecimal amount,

        @NotNull(message = "Date is required.")
        LocalDate expenseDate,

        @NotNull(message = "Choose who paid.")
        Long paidById
) {
}