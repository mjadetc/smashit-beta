package com.smashit_beta.backend.service;

import com.smashit_beta.backend.service.ExpenseSplitter.Split;
import com.smashit_beta.backend.service.ExpenseSplitter.Transfer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plain unit tests for the money maths. All amounts are in centavos (100.00 = 10000).
// People are plain Longs here: ANA = 1, BEN = 2, CY = 3, DEE = 4, RAY = 9 (has left).
class ExpenseSplitterTest {

    private static final Long ANA = 1L;
    private static final Long BEN = 2L;
    private static final Long CY = 3L;
    private static final Long DEE = 4L;
    private static final Long RAY = 9L;

    // Exact shares (no rounding).
    private static final long EXACT = 1;
    // Round up to a whole peso.
    private static final long PESO = 100;

    // A shortcut: no contributions, exact shares.
    private Split<Long> split(List<Long> people, List<Long> sharers, Map<Long, Long> paid) {
        return ExpenseSplitter.split(people, sharers, paid, Map.of(), EXACT);
    }

    // ---------- equal splits ----------

    @Test
    void shouldSplitEquallyAndSendEveryoneToThePayer() {
        // Ana paid 300.00 for three people.
        Split<Long> result = split(List.of(ANA, BEN, CY), List.of(ANA, BEN, CY), Map.of(ANA, 30000L));

        assertEquals(Map.of(ANA, 10000L, BEN, 10000L, CY, 10000L), result.shares());
        assertEquals(20000L, result.balances().get(ANA));
        assertEquals(-10000L, result.balances().get(BEN));
        assertEquals(List.of(new Transfer<>(BEN, ANA, 10000L), new Transfer<>(CY, ANA, 10000L)), result.transfers());
        assertEquals(0L, result.surplus());
    }

    @Test
    void shouldGiveLeftoverCentavosToTheFirstSharers() {
        // 100.00 / 3 = 33.33 with 1 centavo left over: the first person gets 33.34.
        Split<Long> result = split(List.of(ANA, BEN, CY), List.of(ANA, BEN, CY), Map.of(BEN, 10000L));

        assertEquals(3334L, result.shares().get(ANA));
        assertEquals(3333L, result.shares().get(BEN));
        assertEquals(3333L, result.shares().get(CY));
        assertEquals(3333L, result.equalShare());
        // The shares add up to exactly the total: no centavo lost or invented.
        assertEquals(10000L, result.shares().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void shouldSettleWithSeveralPayers() {
        // Ana paid 600.00, Ben 300.00; 900.00 / 4 = 225.00 each.
        // Balances: Ana +375, Ben +75, Cy -225, Dee -225.
        Split<Long> result = split(List.of(ANA, BEN, CY, DEE), List.of(ANA, BEN, CY, DEE),
                Map.of(ANA, 60000L, BEN, 30000L));

        assertEquals(37500L, result.balances().get(ANA));
        assertEquals(7500L, result.balances().get(BEN));
        assertEquals(List.of(
                new Transfer<>(CY, ANA, 22500L),
                new Transfer<>(DEE, ANA, 15000L),
                new Transfer<>(DEE, BEN, 7500L)), result.transfers());
    }

    @Test
    void shouldPayBackSomeoneWhoPaidButDoesNotShare() {
        // Ray paid 100.00 but doesn't share. Ana and Ben each pay him 50.00.
        Split<Long> result = split(List.of(ANA, BEN, RAY), List.of(ANA, BEN), Map.of(RAY, 10000L));

        assertEquals(0L, result.shares().get(RAY));
        assertEquals(10000L, result.balances().get(RAY));
        assertEquals(List.of(new Transfer<>(ANA, RAY, 5000L), new Transfer<>(BEN, RAY, 5000L)), result.transfers());
    }

    @Test
    void shouldHandleNoExpenses() {
        Split<Long> result = split(List.of(ANA, BEN), List.of(ANA, BEN), Map.of());

        assertEquals(0L, result.shares().get(ANA));
        assertEquals(0L, result.balances().get(BEN));
        assertTrue(result.transfers().isEmpty());
    }

    // ---------- NEW: contributions ----------

    @Test
    void shouldTakeContributionOffBeforeSplittingWhenContributorDoesNotShare() {
        // Ben paid 1000.00. Ana willingly puts in 200.00 and doesn't share equally.
        // The other 800.00 is split between Ben, Cy and Dee: 266.67, 266.67, 266.66.
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN, CY, DEE), List.of(BEN, CY, DEE),
                Map.of(BEN, 100000L), Map.of(ANA, 20000L), EXACT);

        assertEquals(80000L, result.pool());
        assertEquals(0L, result.shares().get(ANA));
        assertEquals(-20000L, result.balances().get(ANA));
        assertEquals(26667L, result.shares().get(BEN));
        assertEquals(100000L - 26667L, result.balances().get(BEN));
        assertEquals(-26666L, result.balances().get(DEE));
    }

    @Test
    void shouldAddEqualShareOnTopOfContributionWhenContributorAlsoShares() {
        // Same, but Ana ALSO shares equally: 800.00 / 4 = 200.00 each, so Ana owes 400.00.
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN, CY, DEE), List.of(ANA, BEN, CY, DEE),
                Map.of(BEN, 100000L), Map.of(ANA, 20000L), EXACT);

        assertEquals(20000L, result.shares().get(ANA));
        assertEquals(-40000L, result.balances().get(ANA));
        assertEquals(-20000L, result.balances().get(CY));
    }

    @Test
    void shouldGiveSurplusFromGenerousContributionsToTheBiggestPayer() {
        // Ben paid 100.00, but Ana willingly puts in 150.00: nothing is left to split,
        // and the extra 50.00 is kept by Ben (who paid upfront).
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN, CY), List.of(BEN, CY),
                Map.of(BEN, 10000L), Map.of(ANA, 15000L), EXACT);

        assertEquals(0L, result.pool());
        assertEquals(0L, result.shares().get(CY));
        assertEquals(5000L, result.surplus());
        assertEquals(BEN, result.surplusKeeper());
        assertEquals(List.of(new Transfer<>(ANA, BEN, 15000L)), result.transfers());
    }

    @Test
    void shouldReportShortfallWhenNobodySharesEqually() {
        // Ben paid 100.00, Ana contributes 30.00, and nobody shares: 70.00 isn't covered.
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN), List.of(),
                Map.of(BEN, 10000L), Map.of(ANA, 3000L), EXACT);

        assertEquals(7000L, result.shortfall());
    }

    // ---------- NEW: rounding ----------

    @Test
    void shouldRoundSharesUpAndLetTheBiggestPayerKeepTheExtra() {
        // Ben paid 1000.00, Ana contributes 200.00 and doesn't share.
        // 800.00 / 3 = 266.67, rounded up to 267.00 each: 1.00 extra in total, kept by Ben.
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN, CY, DEE), List.of(BEN, CY, DEE),
                Map.of(BEN, 100000L), Map.of(ANA, 20000L), PESO);

        assertEquals(26700L, result.equalShare());
        assertEquals(26700L, result.shares().get(DEE));
        assertEquals(100L, result.surplus());
        assertEquals(BEN, result.surplusKeeper());
        // Ben paid 1000, owes 267, keeps the extra 1: he receives 734.
        assertEquals(73400L, result.balances().get(BEN));
    }

    @Test
    void shouldRoundToFivePesos() {
        // 160.00 / 3 = 53.34 -> 55.00 with 5-peso rounding.
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN, CY), List.of(ANA, BEN, CY),
                Map.of(ANA, 16000L), Map.of(), 500);

        assertEquals(5500L, result.equalShare());
        assertEquals(500L, result.surplus());
    }

    @Test
    void shouldHaveNoKeeperWhenNobodyPaidUpfront() {
        Split<Long> result = ExpenseSplitter.split(List.of(ANA), List.of(ANA), Map.of(), Map.of(), PESO);

        assertNull(result.surplusKeeper());
        assertEquals(0L, result.surplus());
    }

    // ---------- always true ----------

    @Test
    void shouldBringEveryBalanceToZeroAfterTheTransfers() {
        // Several payers, a contribution and rounding all at once.
        Split<Long> result = ExpenseSplitter.split(List.of(ANA, BEN, CY, DEE), List.of(BEN, CY, DEE),
                Map.of(ANA, 12345L, CY, 67890L, DEE, 111L), Map.of(ANA, 5000L), PESO);

        // Apply each transfer to a copy of the balances: everything should end at 0.
        Map<Long, Long> remaining = new HashMap<>(result.balances());
        for (Transfer<Long> transfer : result.transfers()) {
            remaining.merge(transfer.from(), transfer.cents(), Long::sum);
            remaining.merge(transfer.to(), -transfer.cents(), Long::sum);
        }
        assertTrue(remaining.values().stream().allMatch(balance -> balance == 0));
    }
}