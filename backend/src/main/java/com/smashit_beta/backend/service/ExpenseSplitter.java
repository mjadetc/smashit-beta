package com.smashit_beta.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The money maths (FR-041 to FR-043), with no database and no Spring, like BadmintonRules.
//
// All amounts are whole CENTAVOS (long): 1,234.50 pesos = 123450.
// Whole numbers can't have rounding errors, and we decide exactly where each
// leftover centavo goes, so the shares always add up to the exact total.
//
//   share   = total / number of sharers   (sharers = the active participants)
//   balance = what they paid - their share
//             positive = the group owes them; negative = they owe the group
//   transfers = a short list of simple payments "X pays Y" that brings every balance to 0
public final class ExpenseSplitter {

    private ExpenseSplitter() {
    }

    // One payment to settle up: `fromId` pays `toId` this many centavos.
    public record Transfer(Long fromId, Long toId, long cents) {
    }

    // The result. Both maps keep the order of the `people` list given to split().
    public record Split(Map<Long, Long> shares, Map<Long, Long> balances, List<Transfer> transfers) {
    }

    // people   = everyone to report on, in display order (sharers plus anyone else who paid)
    // sharers  = who splits the cost, in display order (a subset of people)
    // paid     = participant id -> centavos they paid (missing = paid nothing)
    public static Split split(List<Long> people, List<Long> sharers, Map<Long, Long> paid) {
        long total = paid.values().stream().mapToLong(Long::longValue).sum();

        // FR-041: equal shares. 100.00 between 3 people = 33.34, 33.33, 33.33:
        // the leftover centavos go one each to the first sharers in the list.
        Map<Long, Long> shareOf = new LinkedHashMap<>();
        if (!sharers.isEmpty()) {
            long base = total / sharers.size();
            long leftover = total % sharers.size();
            for (int i = 0; i < sharers.size(); i++) {
                shareOf.put(sharers.get(i), base + (i < leftover ? 1 : 0));
            }
        }

        Map<Long, Long> shares = new LinkedHashMap<>();
        Map<Long, Long> balances = new LinkedHashMap<>();
        for (Long person : people) {
            long share = shareOf.getOrDefault(person, 0L);
            shares.put(person, share);
            balances.put(person, paid.getOrDefault(person, 0L) - share);
        }

        return new Split(shares, balances, settle(people, balances));
    }

    // Matches the people who owe with the people who are owed, biggest amounts first.
    // Each step settles at least one person completely, so there are at most
    // (number of people - 1) transfers.
    private static List<Transfer> settle(List<Long> people, Map<Long, Long> balances) {
        // Mutable copies: [id, remaining centavos]. Sorted biggest first;
        // the sort is stable, so ties stay in display order.
        List<long[]> owe = new ArrayList<>();
        List<long[]> owed = new ArrayList<>();
        for (Long person : people) {
            long balance = balances.get(person);
            if (balance < 0) {
                owe.add(new long[]{person, -balance});
            } else if (balance > 0) {
                owed.add(new long[]{person, balance});
            }
        }
        Comparator<long[]> biggestFirst = Comparator.comparingLong((long[] entry) -> entry[1]).reversed();
        owe.sort(biggestFirst);
        owed.sort(biggestFirst);

        List<Transfer> transfers = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < owe.size() && j < owed.size()) {
            long[] debtor = owe.get(i);
            long[] creditor = owed.get(j);
            long amount = Math.min(debtor[1], creditor[1]);
            transfers.add(new Transfer(debtor[0], creditor[0], amount));
            debtor[1] -= amount;
            creditor[1] -= amount;
            if (debtor[1] == 0) {
                i++;
            }
            if (creditor[1] == 0) {
                j++;
            }
        }
        // Anything left over happens only when nobody shares the cost (no active participants).
        return transfers;
    }
}