package com.smashit_beta.backend.service;

import com.smashit_beta.backend.service.ExpenseSplitter.Split;
import com.smashit_beta.backend.service.ExpenseSplitter.Transfer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plain unit tests for the money maths. All amounts are in centavos (100.00 = 10000).
// The people: ANA = 1, BEN = 2, CY = 3, DEE = 4, plus RAY = 9, who has left the event.
class ExpenseSplitterTest {

    private static final Long ANA = 1L;
    private static final Long BEN = 2L;
    private static final Long CY = 3L;
    private static final Long DEE = 4L;
    private static final Long RAY = 9L;

    @Test
    void shouldSplitEquallyAndSendEveryoneToThePayer() {
        // Ana paid 300.00 for three people.
        Split split = ExpenseSplitter.split(List.of(ANA, BEN, CY), List.of(ANA, BEN, CY), Map.of(ANA, 30000L));

        assertEquals(Map.of(ANA, 10000L, BEN, 10000L, CY, 10000L), split.shares());
        assertEquals(20000L, split.balances().get(ANA));
        assertEquals(-10000L, split.balances().get(BEN));
        assertEquals(List.of(new Transfer(BEN, ANA, 10000L), new Transfer(CY, ANA, 10000L)), split.transfers());
    }

    @Test
    void shouldGiveLeftoverCentavosToTheFirstSharers() {
        // 100.00 / 3 = 33.33 with 1 centavo left over: the first person gets 33.34.
        Split split = ExpenseSplitter.split(List.of(ANA, BEN, CY), List.of(ANA, BEN, CY), Map.of(BEN, 10000L));

        assertEquals(3334L, split.shares().get(ANA));
        assertEquals(3333L, split.shares().get(BEN));
        assertEquals(3333L, split.shares().get(CY));
        // The shares add up to exactly the total: no centavo lost or invented.
        assertEquals(10000L, split.shares().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void shouldSettleWithSeveralPayers() {
        // Ana paid 600.00, Ben 300.00; 900.00 / 4 = 225.00 each.
        // Balances: Ana +375, Ben +75, Cy -225, Dee -225.
        Split split = ExpenseSplitter.split(List.of(ANA, BEN, CY, DEE), List.of(ANA, BEN, CY, DEE),
                Map.of(ANA, 60000L, BEN, 30000L));

        assertEquals(37500L, split.balances().get(ANA));
        assertEquals(7500L, split.balances().get(BEN));
        assertEquals(List.of(
                new Transfer(CY, ANA, 22500L),
                new Transfer(DEE, ANA, 15000L),
                new Transfer(DEE, BEN, 7500L)), split.transfers());
    }

    @Test
    void shouldPayBackSomeoneWhoPaidButNoLongerShares() {
        // Ray paid 100.00, then left. Only Ana and Ben share it, so they each pay him 50.00.
        Split split = ExpenseSplitter.split(List.of(ANA, BEN, RAY), List.of(ANA, BEN), Map.of(RAY, 10000L));

        assertEquals(0L, split.shares().get(RAY));
        assertEquals(10000L, split.balances().get(RAY));
        assertEquals(List.of(new Transfer(ANA, RAY, 5000L), new Transfer(BEN, RAY, 5000L)), split.transfers());
    }

    @Test
    void shouldBringEveryBalanceToZeroAfterTheTransfers() {
        Split split = ExpenseSplitter.split(List.of(ANA, BEN, CY, DEE), List.of(ANA, BEN, CY, DEE),
                Map.of(ANA, 12345L, CY, 67890L, DEE, 111L));

        // Apply each transfer to a copy of the balances: everything should end at 0.
        Map<Long, Long> remaining = new java.util.HashMap<>(split.balances());
        for (Transfer transfer : split.transfers()) {
            remaining.merge(transfer.fromId(), transfer.cents(), Long::sum);
            remaining.merge(transfer.toId(), -transfer.cents(), Long::sum);
        }
        assertTrue(remaining.values().stream().allMatch(balance -> balance == 0));
    }

    @Test
    void shouldHandleNoExpenses() {
        Split split = ExpenseSplitter.split(List.of(ANA, BEN), List.of(ANA, BEN), Map.of());

        assertEquals(0L, split.shares().get(ANA));
        assertEquals(0L, split.balances().get(BEN));
        assertTrue(split.transfers().isEmpty());
    }
}