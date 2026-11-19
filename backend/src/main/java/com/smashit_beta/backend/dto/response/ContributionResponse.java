package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.Contribution;

import java.math.BigDecimal;
import java.time.Instant;

// NEW (V11): one contribution, e.g. "Ana: 200.00 for Shuttlecocks".
// expenseId / expenseDescription are null when it isn't linked to an expense.
public record ContributionResponse(
        Long id,
        PlayerRef participant,
        BigDecimal amount,
        Long expenseId,
        String expenseDescription,
        String note,
        Instant createdAt
) {

    public static ContributionResponse from(Contribution contribution) {
        return new ContributionResponse(
                contribution.getId(),
                PlayerRef.from(contribution.getParticipant()),
                contribution.getAmount(),
                contribution.getExpense() == null ? null : contribution.getExpense().getId(),
                contribution.getExpense() == null ? null : contribution.getExpense().getDescription(),
                contribution.getNote(),
                contribution.getCreatedAt());
    }
}