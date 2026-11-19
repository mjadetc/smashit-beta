package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.EventRequest;
import com.smashit_beta.backend.dto.response.EventResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventStatus;
import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.User;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.EventRepository;
import com.smashit_beta.backend.repository.UserRepository;
import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.repository.MatchRepository;

import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Business logic for events. Every method takes the logged-in organizer's email
// (from the session) so it can only ever touch THAT organizer's events.
@Service
public class EventService {

    // Matches that haven't finished yet (they occupy a court).
    private static final List<MatchStatus> OPEN_STATUSES = List.of(MatchStatus.SCHEDULED, MatchStatus.IN_PROGRESS);

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    // To look at the event's unfinished matches.
    private final MatchRepository matchRepository;

    public EventService(EventRepository eventRepository, UserRepository userRepository,
                        MatchRepository matchRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
    }

    // FR-005: create an event. The logged-in user automatically becomes the organizer.
    @Transactional
    public EventResponse create(String organizerEmail, EventRequest request) {
        validate(request);

        User organizer = findOrganizer(organizerEmail);

        Event event = new Event(
                organizer,
                request.name().trim(),
                request.location().trim(),
                request.startsAt(),
                request.endsAt(),
                request.courtCount(),
                request.matchFormat(),
                request.changeEndsAt(),
                normalizeDescription(request.description())
        );

        // save() runs the INSERT and fills in the generated id.
        Event savedEvent = eventRepository.save(event);
        return EventResponse.from(savedEvent);
    }

    // FR-007: list the organizer's own events with the given status.
    // CHANGED: takes a status, so completed/cancelled events are hidden unless asked for.
    @Transactional(readOnly = true)
    public List<EventResponse> listMyEvents(String organizerEmail, EventStatus status) {
        User organizer = findOrganizer(organizerEmail);

        // ACTIVE events: soonest first (what's coming up next).
        // COMPLETED / CANCELLED: most recent first (history).
        Sort sort = (status == EventStatus.ACTIVE)
                ? Sort.by(Sort.Direction.ASC, "startsAt")
                : Sort.by(Sort.Direction.DESC, "startsAt");
        // The line above uses the "ternary operator": condition ? valueIfTrue : valueIfFalse.
        // "startsAt" is the Java FIELD name in Event, not the column name starts_at.

        // .stream().map(...).toList() converts each Event into an EventResponse.
        return eventRepository.findByOrganizerIdAndStatus(organizer.getId(), status, sort)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    // FR-007 / FR-009: view one event.
    @Transactional(readOnly = true)
    public EventResponse getMyEvent(String organizerEmail, Long eventId) {
        Event event = findOwnedEvent(organizerEmail, eventId);
        return EventResponse.from(event);
    }

    // FR-007: update an event.
    @Transactional
    public EventResponse update(String organizerEmail, Long eventId, EventRequest request) {
        validate(request);

        Event event = findOwnedEvent(organizerEmail, eventId);

        // NEW: only ACTIVE events can be edited -> otherwise 409 Conflict.
        if (!event.isActive()) {
            throw new InvalidStateException(
                    "Only active events can be edited. Reactivate it first."
            );
        }

        // An edit must not break a match that is still on court.
        checkChangesAgainstOpenMatches(event, request);

        event.updateDetails(
                request.name().trim(),
                request.location().trim(),
                request.startsAt(),
                request.endsAt(),
                request.courtCount(),
                request.matchFormat(),
                request.changeEndsAt(),
                normalizeDescription(request.description())
        );

        // No save() call needed! The event was loaded inside this @Transactional method,
        // so Hibernate is "watching" it ("dirty checking"). When the method ends and the
        // transaction commits, Hibernate notices the changes and runs the UPDATE itself.
        return EventResponse.from(event);
    }

    // Mark an event as successfully finished. It's hidden from the default list.
    @Transactional
    public EventResponse complete(String organizerEmail, Long eventId) {
        Event event = findOwnedEvent(organizerEmail, eventId);

        // The entity checks its own rules and changes its status.
        // Dirty checking saves the change at commit, just like update().
        event.complete();

        // The session is over, so matches that never finished are cancelled.
        // (Cancelled matches never count for statistics, FR-035.)
        cancelOpenMatches(event);

        return EventResponse.from(event);
    }

    // NEW: call off an event. It stays in the database but is hidden from the default list.
    @Transactional
    public EventResponse cancel(String organizerEmail, Long eventId) {
        Event event = findOwnedEvent(organizerEmail, eventId);
        event.cancel();

        // Same as complete(): nobody plays at a called-off event.
        cancelOpenMatches(event);

        return EventResponse.from(event);
    }

    // NEW: set a completed or cancelled event back to ACTIVE.
    @Transactional
    public EventResponse reactivate(String organizerEmail, Long eventId) {
        Event event = findOwnedEvent(organizerEmail, eventId);
        event.reactivate();
        return EventResponse.from(event);
    }

    // FR-008: delete an event. Its participants, matches and expenses are
    // removed too by ON DELETE CASCADE in the database. Asking "are you sure?" is
    // the frontend's job.
    @Transactional
    public void delete(String organizerEmail, Long eventId) {
        Event event = findOwnedEvent(organizerEmail, eventId);
        eventRepository.delete(event);
    }

    // ---------- private helpers ----------

    // Rules that involve more than one field, which annotations on EventRequest can't express.
    // IllegalArgumentException -> GlobalExceptionHandler returns 400 Bad Request.
    private void validate(EventRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("End time must be after the start time.");
        }

        if (request.changeEndsAt() != null && request.matchFormat() != MatchFormat.SINGLE_GAME) {
            throw new IllegalArgumentException(
                    "Change of ends can only be set for the single-game format."
            );
        }
    }

    // An edit must not break a match that is still on court.
    //  - The format and change-of-ends score decide how a match is scored, so they
    //    can't change while a match is being played.
    //  - Courts can't be removed while one of them still has an unfinished match.
    private void checkChangesAgainstOpenMatches(Event event, EventRequest request) {
        List<Match> openMatches = matchRepository.findByEventIdAndStatusIn(event.getId(), OPEN_STATUSES);

        // Objects.equals compares safely even when a value is null (changeEndsAt can be).
        boolean rulesChange = event.getMatchFormat() != request.matchFormat()
                || !Objects.equals(event.getChangeEndsAt(), request.changeEndsAt());
        boolean matchBeingPlayed = openMatches.stream()
                .anyMatch(match -> match.getStatus() == MatchStatus.IN_PROGRESS);
        if (rulesChange && matchBeingPlayed) {
            throw new InvalidStateException(
                    "The match format and change of ends can't change while a match is being played.");
        }

        // The highest court number that still has an unfinished match (0 if none).
        int highestBusyCourt = openMatches.stream().mapToInt(Match::getCourtNumber).max().orElse(0);
        if (request.courtCount() < highestBusyCourt) {
            throw new InvalidStateException("Court " + highestBusyCourt
                    + " still has a match that hasn't finished, so the event needs at least "
                    + highestBusyCourt + " courts.");
        }
    }

    // Cancels every scheduled or in-progress match of the event.
    // Dirty checking saves the changes at commit.
    private void cancelOpenMatches(Event event) {
        matchRepository.findByEventIdAndStatusIn(event.getId(), OPEN_STATUSES)
                .forEach(Match::cancel);
    }

    private User findOrganizer(String organizerEmail) {
        return userRepository.findByEmailIgnoreCase(organizerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    // Loads an event ONLY if it belongs to this organizer.
    // Someone else's event gives the same 404 as a missing one, so other users
    // can't even discover which event ids exist (NFR-002).
    // CHANGED: public (was private) so other services, like ParticipantService,
    // reuse this exact ownership check instead of writing their own.
    public Event findOwnedEvent(String organizerEmail, Long eventId) {
        User organizer = findOrganizer(organizerEmail);

        return eventRepository.findByIdAndOrganizerId(eventId, organizer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found."));
    }

    // Stores an empty or whitespace-only description as NULL instead of "".
    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}