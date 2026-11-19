package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

// FR-042 / FR-043: the money at a glance.
public record ExpenseSummaryResponse(
        Long eventId,
        BigDecimal totalAmount,
        // how many people split the total (the active participants)
        int sharingParticipants,
        // the total per category, only categories that were used
        List<CategoryTotal> categories,
        // every active participant, plus anyone removed who paid something
        List<Balance> balances,
        // "who pays whom" to settle up
        List<Settlement> settlements
) {

    public record CategoryTotal(ExpenseCategory category, BigDecimal amount) {
    }

    // balance = paid - share. Positive: the group owes them. Negative: they owe.
    public record Balance(
            Long participantId,
            String playerName,
            boolean active,
            BigDecimal paid,
            BigDecimal share,
            BigDecimal balance
    ) {
    }

    // `from` pays `to` this amount.
    public record Settlement(PlayerRef from, PlayerRef to, BigDecimal amount) {
    }
}