package com.smashit_beta.backend.dto.request;

import com.smashit_beta.backend.entity.ExpenseCategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// FR-040: the JSON body for recording or editing an expense.
// CHANGED (V11): give EITHER paidById (a participant) OR outsidePayerName (someone
// outside the event). For an outside payer, outsidePayerRepaid = false means it was a
// gift (it comes off the total); missing or true means they get paid back. Examples:
//   { ..., "paidById": 12 }
//   { ..., "outsidePayerName": "Coach Jun" }                                (paid back)
//   { ..., "outsidePayerName": "Coach Jun", "outsidePayerRepaid": false }   (a gift)
// "Exactly one payer" involves two fields, so it's checked in ExpenseService.
public record ExpenseRequest(

        @NotBlank(message = "Description is required.")
        @Size(max = 255, message = "Description must be at most 255 characters.")
        String description,

        @NotNull(message = "Category is required.")
        ExpenseCategory category,

        // @Digits matches NUMERIC(10, 2): up to 8 digits before the point, 2 after.
        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01.")
        @Digits(integer = 8, fraction = 2, message = "Amount can have at most 8 digits and 2 decimal places.")
        BigDecimal amount,

        @NotNull(message = "Date is required.")
        LocalDate expenseDate,

        // CHANGED: no longer required on its own.
        Long paidById,

        // NEW
        @Size(max = 80, message = "The payer's name must be at most 80 characters.")
        String outsidePayerName,

        // NEW: Boolean (not boolean) so "not given" arrives as null.
        Boolean outsidePayerRepaid
) {
}