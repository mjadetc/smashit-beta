package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.ExpenseRequest;
import com.smashit_beta.backend.dto.request.ShareRoundingRequest;
import com.smashit_beta.backend.dto.request.SharingRequest;
import com.smashit_beta.backend.dto.response.ExpenseResponse;
import com.smashit_beta.backend.dto.response.ExpenseSummaryResponse;
import com.smashit_beta.backend.dto.response.ExpenseSummaryResponse.Balance;
import com.smashit_beta.backend.dto.response.ExpenseSummaryResponse.CategoryTotal;
import com.smashit_beta.backend.dto.response.ExpenseSummaryResponse.Receiver;
import com.smashit_beta.backend.dto.response.ExpenseSummaryResponse.Settlement;
import com.smashit_beta.backend.dto.response.PlayerRef;
import com.smashit_beta.backend.dto.response.SharingResponse;
import com.smashit_beta.backend.entity.Contribution;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.Expense;
import com.smashit_beta.backend.entity.ExpenseCategory;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.ContributionRepository;
import com.smashit_beta.backend.repository.EventParticipantRepository;
import com.smashit_beta.backend.repository.ExpenseRepository;
import com.smashit_beta.backend.repository.MatchPlayerRepository;
import com.smashit_beta.backend.repository.MatchPlayerRepository.ParticipantMatchStats;
import com.smashit_beta.backend.service.ExpenseSplitter.Split;
import com.smashit_beta.backend.service.ExpenseSplitter.Transfer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Event expenses (FR-039 to FR-043).
//
// Expenses can be recorded for an event in ANY status: people often settle up after
// the session (COMPLETED), and a called-off event (CANCELLED) can still have a
// non-refundable court booking to split.
//
// CHANGED (V11): outside payers, contributions (see ContributionService), the
// "shares equally" toggle per participant, and rounding.
@Service
public class ExpenseService {

    private final EventService eventService;
    private final EventParticipantRepository participantRepository;
    private final ExpenseRepository expenseRepository;
    // NEW
    private final ContributionRepository contributionRepository;
    // NEW: who finished a match (part of the default "shares equally" rule).
    private final MatchPlayerRepository matchPlayerRepository;

    public ExpenseService(EventService eventService, EventParticipantRepository participantRepository,
                          ExpenseRepository expenseRepository, ContributionRepository contributionRepository,
                          MatchPlayerRepository matchPlayerRepository) {
        this.eventService = eventService;
        this.participantRepository = participantRepository;
        this.expenseRepository = expenseRepository;
        this.contributionRepository = contributionRepository;
        this.matchPlayerRepository = matchPlayerRepository;
    }

    // Identifies a person in the summary: a participant (by id) OR an outside payer
    // (by their name in lower case, so "Coach Jun" and "coach jun " are the same person).
    // A record gets equals()/hashCode() for free, so it works as a Map key.
    private record Party(Long participantId, String outsiderKey) {
        static Party of(EventParticipant participant) {
            return new Party(participant.getId(), null);
        }

        static Party outsider(String name) {
            return new Party(null, name.trim().toLowerCase(Locale.ROOT));
        }
    }

    // ---------- expenses ----------

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        return expenseRepository.findByEventIdOrderByExpenseDateAscIdAsc(event.getId())
                .stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    // FR-039 / FR-040: record an expense.
    @Transactional
    public ExpenseResponse create(String organizerEmail, Long eventId, ExpenseRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);

        Expense expense = new Expense(event, request.description().trim(), request.category(),
                request.amount(), request.expenseDate());
        applyPayer(event, expense, request);
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(String organizerEmail, Long eventId, Long expenseId, ExpenseRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Expense expense = findExpense(event, expenseId);

        expense.updateDetails(request.description().trim(), request.category(),
                request.amount(), request.expenseDate());
        applyPayer(event, expense, request);
        return ExpenseResponse.from(expense);
    }

    // A real delete: an expense entered by mistake should simply disappear.
    // A contribution labelled "for" it stays (the money was still given), just without the label.
    @Transactional
    public void delete(String organizerEmail, Long eventId, Long expenseId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Expense expense = findExpense(event, expenseId);

        // Unlink in Java first, so Hibernate's in-memory objects never point at a deleted
        // expense. (ON DELETE SET NULL in V11 is the database's safety net for the same thing.)
        contributionRepository.findByExpenseId(expense.getId()).forEach(Contribution::unlinkExpense);
        expenseRepository.delete(expense);
    }

    // ---------- NEW: who shares equally ----------

    // Every participant (active or removed) and whether they pay an equal share.
    @Transactional(readOnly = true)
    public List<SharingResponse> sharing(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Set<Long> played = playedAMatch(event);
        return participantRepository.findByEventIdOrderByPlayerNameAsc(event.getId())
                .stream()
                .map(participant -> toSharing(participant, played))
                .toList();
    }

    // The toggle: true / false, or null to go back to the default rule.
    @Transactional
    public SharingResponse updateSharing(String organizerEmail, Long eventId, Long participantId,
                                         SharingRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        EventParticipant participant = participantRepository.findByIdAndEventId(participantId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found."));

        participant.changeSharesEqually(request.sharesEqually());
        return toSharing(participant, playedAMatch(event));
    }

    // NEW: choose how equal shares are rounded up. Returns the updated summary.
    @Transactional
    public ExpenseSummaryResponse updateRounding(String organizerEmail, Long eventId, ShareRoundingRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        event.changeShareRounding(request.shareRounding());
        return buildSummary(event);
    }

    // ---------- summary ----------

    // FR-041 to FR-043: totals, each person's share and balance, and who pays whom.
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(String organizerEmail, Long eventId) {
        return buildSummary(eventService.findOwnedEvent(organizerEmail, eventId));
    }

    private ExpenseSummaryResponse buildSummary(Event event) {
        List<Expense> expenses = expenseRepository.findByEventIdOrderByExpenseDateAscIdAsc(event.getId());
        List<Contribution> contributionList = contributionRepository.findByEventIdOrderByIdAsc(event.getId());
        List<EventParticipant> everyone = participantRepository.findByEventIdOrderByPlayerNameAsc(event.getId());
        Set<Long> played = playedAMatch(event);

        // 1) What each person paid upfront that must be paid back. Gifts only reduce the total.
        Map<Party, Long> paid = new HashMap<>();
        Map<Party, String> outsiderNames = new HashMap<>();
        long gifts = 0;
        for (Expense expense : expenses) {
            long cents = toCents(expense.getAmount());
            if (expense.isGift()) {
                gifts += cents;
            } else if (expense.isPaidByOutsider()) {
                Party outsider = Party.outsider(expense.getOutsidePayerName());
                paid.merge(outsider, cents, Long::sum);
                // Show the name as it was first typed.
                outsiderNames.putIfAbsent(outsider, expense.getOutsidePayerName().trim());
            } else {
                paid.merge(Party.of(expense.getPaidBy()), cents, Long::sum);
            }
        }

        // 2) Contributions per participant.
        Map<Party, Long> contributions = new HashMap<>();
        for (Contribution contribution : contributionList) {
            contributions.merge(Party.of(contribution.getParticipant()), toCents(contribution.getAmount()), Long::sum);
        }

        // 3) Who shares equally, and who to show: participants A to Z (sharers, plus anyone
        //    who paid or contributed), then outside payers A to Z.
        List<Party> sharers = new ArrayList<>();
        List<Party> people = new ArrayList<>();
        Map<Party, EventParticipant> participantOf = new HashMap<>();
        for (EventParticipant participant : everyone) {
            Party party = Party.of(participant);
            boolean shares = sharesEqually(participant, played);
            if (shares) {
                sharers.add(party);
            }
            if (shares || paid.containsKey(party) || contributions.containsKey(party)) {
                people.add(party);
                participantOf.put(party, participant);
            }
        }
        outsiderNames.keySet().stream()
                .sorted(Comparator.comparing(Party::outsiderKey))
                .forEach(people::add);

        Split<Party> split = ExpenseSplitter.split(people, sharers, paid, contributions,
                event.getShareRounding().unitCents());

        // 4) Turn centavos and Party keys back into amounts and names.
        Map<Party, PlayerRef> refs = new LinkedHashMap<>();
        for (Party party : people) {
            EventParticipant participant = participantOf.get(party);
            refs.put(party, participant != null
                    ? PlayerRef.from(participant)
                    : new PlayerRef(null, outsiderNames.get(party)));
        }

        List<Balance> balances = people.stream()
                .map(party -> {
                    EventParticipant participant = participantOf.get(party);
                    long contribution = contributions.getOrDefault(party, 0L);
                    long share = split.shares().get(party);
                    long kept = party.equals(split.surplusKeeper()) ? split.surplus() : 0;
                    return new Balance(
                            refs.get(party),
                            participant == null,
                            participant != null && participant.isActive(),
                            sharers.contains(party),
                            toAmount(paid.getOrDefault(party, 0L)),
                            toAmount(contribution),
                            toAmount(share),
                            toAmount(contribution + share),
                            toAmount(kept),
                            toAmount(split.balances().get(party)));
                })
                .toList();

        List<Settlement> settlements = split.transfers().stream()
                .map(transfer -> toSettlement(transfer, refs))
                .toList();

        // The same payments, grouped by who receives them, in display order.
        List<Receiver> receivers = people.stream()
                .filter(party -> split.balances().get(party) > 0)
                .map(party -> new Receiver(
                        refs.get(party),
                        toAmount(split.balances().get(party)),
                        split.transfers().stream()
                                .filter(transfer -> transfer.to().equals(party))
                                .map(transfer -> toSettlement(transfer, refs))
                                .toList()))
                .toList();

        long total = expenses.stream().mapToLong(expense -> toCents(expense.getAmount())).sum();
        long contributed = contributions.values().stream().mapToLong(Long::longValue).sum();

        return new ExpenseSummaryResponse(
                event.getId(),
                event.getShareRounding(),
                toAmount(total),
                toAmount(gifts),
                toAmount(total - gifts),
                toAmount(contributed),
                toAmount(split.pool()),
                sharers.size(),
                toAmount(split.equalShare()),
                toAmount(split.surplus()),
                split.surplusKeeper() == null ? null : refs.get(split.surplusKeeper()),
                toAmount(split.shortfall()),
                categoryTotals(expenses),
                balances,
                settlements,
                receivers);
    }

    // ---------- private helpers ----------

    private Expense findExpense(Event event, Long expenseId) {
        return expenseRepository.findByIdAndEventId(expenseId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found."));
    }

    // Exactly one payer: a participant OR an outside person.
    private void applyPayer(Event event, Expense expense, ExpenseRequest request) {
        boolean hasParticipant = request.paidById() != null;
        boolean hasOutsider = request.outsidePayerName() != null && !request.outsidePayerName().isBlank();
        if (hasParticipant == hasOutsider) {
            throw new IllegalArgumentException(
                    "Choose who paid: a participant, or the name of someone outside the event (not both).");
        }

        if (hasOutsider) {
            // Missing "repaid" = pay them back (the safe default).
            boolean repaid = request.outsidePayerRepaid() == null || request.outsidePayerRepaid();
            expense.paidByOutsider(request.outsidePayerName().trim(), repaid);
            return;
        }

        // The same payer as before is kept even if they've left the event since:
        // an expense paid by someone who left can still have its amount fixed.
        EventParticipant current = expense.getPaidBy();
        if (current != null && current.getId().equals(request.paidById())) {
            return;
        }
        EventParticipant payer = participantRepository.findByIdAndEventId(request.paidById(), event.getId())
                .filter(EventParticipant::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The payer must be an active participant of this event."));
        expense.paidByParticipant(payer);
    }

    // Ids of participants who finished at least one match in this event.
    private Set<Long> playedAMatch(Event event) {
        return matchPlayerRepository.findStatsPerParticipant(event.getId(), MatchStatus.COMPLETED)
                .stream()
                .map(ParticipantMatchStats::getParticipantId)
                .collect(Collectors.toSet());
    }

    // The organizer's choice if they made one; otherwise the default rule:
    // active, or finished at least one match (so someone who left early still shares).
    private static boolean sharesEqually(EventParticipant participant, Set<Long> played) {
        if (participant.getSharesEqually() != null) {
            return participant.getSharesEqually();
        }
        return participant.isActive() || played.contains(participant.getId());
    }

    private static SharingResponse toSharing(EventParticipant participant, Set<Long> played) {
        return new SharingResponse(
                participant.getId(),
                participant.getPlayerName(),
                participant.isActive(),
                played.contains(participant.getId()),
                sharesEqually(participant, played),
                participant.getSharesEqually() != null);
    }

    private static Settlement toSettlement(Transfer<Party> transfer, Map<Party, PlayerRef> refs) {
        return new Settlement(refs.get(transfer.from()), refs.get(transfer.to()), toAmount(transfer.cents()));
    }

    // Totals per category, in the enum's order (court rental first), skipping unused ones.
    // EnumMap = a Map with enum keys that keeps them in declaration order.
    private List<CategoryTotal> categoryTotals(List<Expense> expenses) {
        Map<ExpenseCategory, Long> totals = new EnumMap<>(ExpenseCategory.class);
        for (Expense expense : expenses) {
            totals.merge(expense.getCategory(), toCents(expense.getAmount()), Long::sum);
        }
        return totals.entrySet().stream()
                .map(entry -> new CategoryTotal(entry.getKey(), toAmount(entry.getValue())))
                .toList();
    }

    // 1234.50 -> 123450. longValueExact() fails loudly instead of silently rounding.
    static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    // 123450 -> 1234.50 (always 2 decimal places).
    static BigDecimal toAmount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}