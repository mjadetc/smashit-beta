package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.ContributionRequest;
import com.smashit_beta.backend.dto.response.ContributionResponse;
import com.smashit_beta.backend.entity.Contribution;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.Expense;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.ContributionRepository;
import com.smashit_beta.backend.repository.EventParticipantRepository;
import com.smashit_beta.backend.repository.ExpenseRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// NEW (V11): money participants willingly put in ("Ana: 200 for the shuttlecocks").
// The maths happens in ExpenseService's summary; this class only records them.
@Service
public class ContributionService {

    private final EventService eventService;
    private final EventParticipantRepository participantRepository;
    private final ExpenseRepository expenseRepository;
    private final ContributionRepository contributionRepository;

    public ContributionService(EventService eventService, EventParticipantRepository participantRepository,
                               ExpenseRepository expenseRepository, ContributionRepository contributionRepository) {
        this.eventService = eventService;
        this.participantRepository = participantRepository;
        this.expenseRepository = expenseRepository;
        this.contributionRepository = contributionRepository;
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> list(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        return contributionRepository.findByEventIdOrderByIdAsc(event.getId())
                .stream()
                .map(ContributionResponse::from)
                .toList();
    }

    @Transactional
    public ContributionResponse create(String organizerEmail, Long eventId, ContributionRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Contribution contribution = new Contribution(event, findContributor(event, request.participantId()),
                findLinkedExpense(event, request.expenseId()), request.amount(), normalizeNote(request.note()));
        return ContributionResponse.from(contributionRepository.save(contribution));
    }

    @Transactional
    public ContributionResponse update(String organizerEmail, Long eventId, Long contributionId,
                                       ContributionRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        Contribution contribution = findContribution(event, contributionId);
        contribution.update(findContributor(event, request.participantId()),
                findLinkedExpense(event, request.expenseId()), request.amount(), normalizeNote(request.note()));
        return ContributionResponse.from(contribution);
    }

    @Transactional
    public void delete(String organizerEmail, Long eventId, Long contributionId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        contributionRepository.delete(findContribution(event, contributionId));
    }

    // ---------- private helpers ----------

    private Contribution findContribution(Event event, Long contributionId) {
        return contributionRepository.findByIdAndEventId(contributionId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found."));
    }

    // Any participant of THIS event, active or removed: someone who left early can still chip in.
    private EventParticipant findContributor(Event event, Long participantId) {
        return participantRepository.findByIdAndEventId(participantId, event.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The contributor must be a participant of this event."));
    }

    // The optional "for this expense" label. It must be one of THIS event's expenses.
    private Expense findLinkedExpense(Event event, Long expenseId) {
        if (expenseId == null) {
            return null;
        }
        return expenseRepository.findByIdAndEventId(expenseId, event.getId())
                .orElseThrow(() -> new IllegalArgumentException("That expense isn't part of this event."));
    }

    // Stores an empty or whitespace-only note as NULL instead of "".
    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}