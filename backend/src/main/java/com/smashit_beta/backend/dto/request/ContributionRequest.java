package com.smashit_beta.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// NEW (V11): "this participant willingly puts in this amount", e.g.
// { "participantId": 12, "amount": 200.00, "expenseId": 9, "note": "For the shuttlecocks" }
// expenseId and note are optional labels; they don't change the maths.
public record ContributionRequest(

        @NotNull(message = "Choose who contributes.")
        Long participantId,

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01.")
        @Digits(integer = 8, fraction = 2, message = "Amount can have at most 8 digits and 2 decimal places.")
        BigDecimal amount,

        Long expenseId,

        @Size(max = 255, message = "Note must be at most 255 characters.")
        String note
) {
}