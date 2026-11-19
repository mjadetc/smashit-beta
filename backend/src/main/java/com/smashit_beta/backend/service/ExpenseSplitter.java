package com.smashit_beta.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The money maths (FR-041 to FR-043), with no database and no Spring, like BadmintonRules.
//
// All amounts are whole CENTAVOS (long): 1,234.50 pesos = 123450.
//
// CHANGED (V11): the steps are now
//   1. toCover   = everything paid upfront that must be paid back (gifts are left out)
//   2. pool      = toCover - all contributions          (never below 0)
//   3. share     = pool / number of sharers, rounded up to the event's step (or exact)
//   4. owes      = their contribution + their share (a share only if they share equally)
//   5. surplus   = what everyone owes - toCover       (from rounding up, or very generous
//                  contributions). The person who paid the most upfront keeps it.
//   6. balance   = paid - owes (+ surplus for the one who keeps it)
//                  positive = they receive money; negative = they pay
//   7. transfers = a short list of payments "X pays Y" that brings every balance to 0
//
// <K> is a "type parameter": the class doesn't care WHAT identifies a person
// (a participant, or an outside payer's name), only that two equal keys mean the same person.
public final class ExpenseSplitter {

    private ExpenseSplitter() {
    }

    // One payment to settle up: `from` pays `to` this many centavos.
    public record Transfer<K>(K from, K to, long cents) {
    }

    // The result. The maps keep the order of the `people` list given to split().
    //   equalShare    = what one sharer pays (exact split: the smallest share; a few
    //                   people may pay 1 centavo more)
    //   surplusKeeper = who keeps the surplus, or null if nobody paid anything upfront
    //   shortfall     = money nobody is paying for (only when NOBODY shares equally and the
    //                   contributions don't cover everything), so the UI can warn about it
    public record Split<K>(
            Map<K, Long> shares,
            Map<K, Long> balances,
            List<Transfer<K>> transfers,
            long pool,
            long equalShare,
            long surplus,
            K surplusKeeper,
            long shortfall
    ) {
    }

    // people        = everyone to report on, in display order
    // sharers       = who pays an equal share, in display order (a subset of people)
    // paid          = person -> centavos paid upfront that must be paid back (missing = 0)
    // contributions = person -> centavos they willingly put in (missing = 0)
    // roundingUnit  = 1 for exact shares, 100 to round up to a whole peso, 500, 1000, ...
    public static <K> Split<K> split(List<K> people, List<K> sharers, Map<K, Long> paid,
                                     Map<K, Long> contributions, long roundingUnit) {
        long toCover = sum(paid);
        long pool = Math.max(0, toCover - sum(contributions));

        // Equal shares of the pool.
        Map<K, Long> shareOf = new LinkedHashMap<>();
        long equalShare = 0;
        if (!sharers.isEmpty()) {
            int count = sharers.size();
            if (roundingUnit <= 1) {
                // Exact: 100.00 between 3 people = 33.34, 33.33, 33.33.
                // The leftover centavos go one each to the first sharers in the list.
                equalShare = pool / count;
                long leftover = pool % count;
                for (int i = 0; i < count; i++) {
                    shareOf.put(sharers.get(i), equalShare + (i < leftover ? 1 : 0));
                }
            } else {
                // Rounded up: everyone pays the same, slightly more than needed.
                equalShare = ceilDiv(ceilDiv(pool, count), roundingUnit) * roundingUnit;
                for (K sharer : sharers) {
                    shareOf.put(sharer, equalShare);
                }
            }
        }

        // What each person owes in total, and the surplus that creates.
        Map<K, Long> shares = new LinkedHashMap<>();
        long totalOwed = 0;
        for (K person : people) {
            long share = shareOf.getOrDefault(person, 0L);
            shares.put(person, share);
            totalOwed += share + contributions.getOrDefault(person, 0L);
        }
        long surplus = Math.max(0, totalOwed - toCover);
        long shortfall = Math.max(0, toCover - totalOwed);

        // The surplus goes to whoever paid the most upfront (the first one on a tie).
        K keeper = null;
        long mostPaid = 0;
        for (K person : people) {
            long amount = paid.getOrDefault(person, 0L);
            if (amount > mostPaid) {
                mostPaid = amount;
                keeper = person;
            }
        }

        Map<K, Long> balances = new LinkedHashMap<>();
        for (K person : people) {
            long owes = shares.get(person) + contributions.getOrDefault(person, 0L);
            long balance = paid.getOrDefault(person, 0L) - owes;
            if (person.equals(keeper)) {
                balance += surplus;
            }
            balances.put(person, balance);
        }

        return new Split<>(shares, balances, settle(people, balances), pool, equalShare, surplus, keeper, shortfall);
    }

    // Matches the people who pay with the people who receive, biggest amounts first.
    // Each step settles at least one person completely, so there are at most
    // (number of people - 1) transfers.
    private static <K> List<Transfer<K>> settle(List<K> people, Map<K, Long> balances) {
        // [person, remaining centavos], biggest first. The sort is stable,
        // so ties stay in display order.
        List<Remaining<K>> payers = new ArrayList<>();
        List<Remaining<K>> receivers = new ArrayList<>();
        for (K person : people) {
            long balance = balances.get(person);
            if (balance < 0) {
                payers.add(new Remaining<>(person, -balance));
            } else if (balance > 0) {
                receivers.add(new Remaining<>(person, balance));
            }
        }
        Comparator<Remaining<K>> biggestFirst = Comparator.comparingLong((Remaining<K> r) -> r.cents).reversed();
        payers.sort(biggestFirst);
        receivers.sort(biggestFirst);

        List<Transfer<K>> transfers = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < payers.size() && j < receivers.size()) {
            Remaining<K> payer = payers.get(i);
            Remaining<K> receiver = receivers.get(j);
            long amount = Math.min(payer.cents, receiver.cents);
            transfers.add(new Transfer<>(payer.person, receiver.person, amount));
            payer.cents -= amount;
            receiver.cents -= amount;
            if (payer.cents == 0) {
                i++;
            }
            if (receiver.cents == 0) {
                j++;
            }
        }
        // Money is left unmatched only in two rare cases: nobody paid anything upfront
        // (nobody to pay), or nobody shares equally (the shortfall).
        return transfers;
    }

    // A person and how much they still have to pay or receive while settling.
    private static class Remaining<K> {
        final K person;
        long cents;

        Remaining(K person, long cents) {
            this.person = person;
            this.cents = cents;
        }
    }

    private static <K> long sum(Map<K, Long> amounts) {
        return amounts.values().stream().mapToLong(Long::longValue).sum();
    }

    // Division that rounds UP: ceilDiv(10, 3) = 4. (Only for positive numbers.)
    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}