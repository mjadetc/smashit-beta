package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.ParticipantRequest;
import com.smashit_beta.backend.dto.response.ParticipantResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.exception.DuplicateResourceException;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.EventParticipantRepository;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.repository.MatchPlayerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// Business logic for an event's participants (FR-010 to FR-013).
// Every method first loads the event through EventService.findOwnedEvent, which
// throws 404 if the event doesn't exist OR belongs to another organizer.
// So ownership is checked in ONE place and reused here.
@Service
public class ParticipantService {

    private final EventService eventService;
    private final EventParticipantRepository participantRepository;
    // To check whether a participant is on court right now.
    private final MatchPlayerRepository matchPlayerRepository;

    // A service can depend on another service; Spring injects all three.
    public ParticipantService(
            EventService eventService,
            EventParticipantRepository participantRepository,
            MatchPlayerRepository matchPlayerRepository
    ) {
        this.eventService = eventService;
        this.participantRepository = participantRepository;
        this.matchPlayerRepository = matchPlayerRepository;
    }

    // List the event's current participants. Allowed for any event status,
    // so a completed event still shows who played.
    @Transactional(readOnly = true)
    public List<ParticipantResponse> list(String organizerEmail, Long eventId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);

        return participantRepository.findByEventIdAndActiveTrueOrderByPlayerNameAsc(event.getId())
                .stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    // FR-010: add a participant by name.
    @Transactional
    public ParticipantResponse add(String organizerEmail, Long eventId, ParticipantRequest request) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);

        String playerName = normalizeName(request.playerName());

        // Is there already someone with this name in the event (active or removed)?
        Optional<EventParticipant> existing =
                participantRepository.findByEventIdAndPlayerNameIgnoreCase(event.getId(), playerName);

        if (existing.isPresent()) {
            EventParticipant participant = existing.get();

            if (participant.isActive()) {
                throw new DuplicateResourceException(
                        "A participant named \"" + participant.getPlayerName()
                                + "\" is already in this event."
                );
            }

            // They were removed earlier: bring the SAME row back, so their past
            // matches still count for matchmaking fairness (FR-015).
            // rename() keeps the spelling the organizer just typed, e.g. "juan" -> "Juan".
            participant.restore();
            participant.rename(playerName);
            return ParticipantResponse.from(participant);
        }

        EventParticipant saved = participantRepository.save(new EventParticipant(event, playerName));
        return ParticipantResponse.from(saved);
    }

    // FR-011: rename a participant.
    @Transactional
    public ParticipantResponse rename(
            String organizerEmail,
            Long eventId,
            Long participantId,
            ParticipantRequest request
    ) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);

        EventParticipant participant = findActiveParticipant(event, participantId);
        String playerName = normalizeName(request.playerName());

        // Someone else (active or removed) already has this name -> 409.
        // The database's unique index would reject it anyway; checking first gives a clear message.
        if (participantRepository.existsByEventIdAndPlayerNameIgnoreCaseAndIdNot(
                event.getId(), playerName, participant.getId())) {
            throw new DuplicateResourceException(
                    "Another participant in this event already uses the name \"" + playerName + "\"."
            );
        }

        // Dirty checking saves this at commit; no save() call needed.
        participant.rename(playerName);
        return ParticipantResponse.from(participant);
    }

    // FR-011: remove a participant. It's a soft delete: the row stays (active = false).
    @Transactional
    public void remove(String organizerEmail, Long eventId, Long participantId) {
        Event event = eventService.findOwnedEvent(organizerEmail, eventId);
        requireActiveEvent(event);

        EventParticipant participant = findActiveParticipant(event, participantId);

        // Someone in a scheduled or in-progress match can't just disappear.
        // (Completed and cancelled matches don't block this: their history stays.)
        if (matchPlayerRepository.existsByParticipantIdAndMatchStatusIn(
                participant.getId(), List.of(MatchStatus.SCHEDULED, MatchStatus.IN_PROGRESS))) {
            throw new InvalidStateException(
                    "\"" + participant.getPlayerName() + "\" is in a match that hasn't finished. "
                            + "Change that match's line-up or cancel it first.");
        }

        participant.remove();
    }

    // ---------- private helpers ----------

    // Completed or cancelled events are read-only (same rule as editing the event).
    private void requireActiveEvent(Event event) {
        if (!event.isActive()) {
            throw new InvalidStateException(
                    "Participants can only be changed while the event is active."
            );
        }
    }

    // The participant must belong to THIS event and must not be removed already.
    private EventParticipant findActiveParticipant(Event event, Long participantId) {
        return participantRepository.findByIdAndEventId(participantId, event.getId())
                // Optional.filter: keep the value only if the condition is true.
                .filter(EventParticipant::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found."));
    }

    // "  Juan   Dela  Cruz " -> "Juan Dela Cruz"
    // trim() removes spaces at both ends; replaceAll("\\s+", " ") turns any run of
    // whitespace inside the name into a single space. (\\s+ is a "regular expression".)
    private String normalizeName(String playerName) {
        return playerName.trim().replaceAll("\\s+", " ");
    }
}