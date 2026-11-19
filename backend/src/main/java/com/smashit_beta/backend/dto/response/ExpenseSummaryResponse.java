package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.ExpenseCategory;
import com.smashit_beta.backend.entity.ShareRounding;

import java.math.BigDecimal;
import java.util.List;

// FR-042 / FR-043: the money at a glance.
// CHANGED (V11): the totals now show how the amount is built up:
//   totalExpenses - gifts              = toCover
//   toCover       - contributionsTotal = sharedEqually  (split between sharingParticipants)
// A "person" (PlayerRef) is a participant, or an outside payer (then participantId is null).
public record ExpenseSummaryResponse(
        Long eventId,
        ShareRounding shareRounding,
        BigDecimal totalExpenses,
        // expenses paid by outside people who don't want the money back
        BigDecimal gifts,
        BigDecimal toCover,
        BigDecimal contributionsTotal,
        BigDecimal sharedEqually,
        int sharingParticipants,
        // what one sharer pays (exact split: a few people may pay 0.01 more)
        BigDecimal equalShare,
        // collected beyond what's needed (rounding, generous contributions); kept by surplusKeptBy
        BigDecimal surplus,
        PlayerRef surplusKeptBy,
        // money nobody pays for: only when no one shares equally (show a warning)
        BigDecimal shortfall,
        List<CategoryTotal> categories,
        List<Balance> balances,
        // "who pays whom", one line per payment
        List<Settlement> settlements,
        // the same payments, grouped by who receives them ("Ben receives 555 from ...")
        List<Receiver> receivers
) {

    public record CategoryTotal(ExpenseCategory category, BigDecimal amount) {
    }

    // One person's numbers:
    //   owes    = contribution + share
    //   balance = paid - owes (+ surplusKept). Positive: they receive. Negative: they pay.
    public record Balance(
            PlayerRef person,
            boolean outsidePayer,
            boolean active,
            boolean sharesEqually,
            BigDecimal paid,
            BigDecimal contribution,
            BigDecimal share,
            BigDecimal owes,
            BigDecimal surplusKept,
            BigDecimal balance
    ) {
    }

    // `from` pays `to` this amount.
    public record Settlement(PlayerRef from, PlayerRef to, BigDecimal amount) {
    }

    // Someone who paid upfront and what they get back, from whom.
    public record Receiver(PlayerRef person, BigDecimal total, List<Settlement> payments) {
    }
}