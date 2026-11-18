package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.EventRequest;
import com.smashit_beta.backend.dto.response.EventResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.User;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.EventRepository;
import com.smashit_beta.backend.repository.UserRepository;
import com.smashit_beta.backend.entity.EventStatus;
import com.smashit_beta.backend.exception.InvalidStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UNIT tests for EventService: no Spring, no database. The repositories are fakes
// (Mockito mocks), so each test controls exactly what the "database" returns.
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final String EMAIL = "organizer@example.com";

    // A fixed time in UTC+8 (Philippine time) so the tests always behave the same.
    private static final OffsetDateTime START =
            OffsetDateTime.of(2026, 11, 20, 19, 0, 0, 0, ZoneOffset.ofHours(8));

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EventService eventService;

    private User organizer;

    @BeforeEach
    void setUp() {
        organizer = new User("Test", "Organizer", "organizer", EMAIL, "hash");

        // User has no setId() (the database assigns ids), but this test needs an id.
        // ReflectionTestUtils is a Spring TEST-ONLY tool that sets a private field directly.
        ReflectionTestUtils.setField(organizer, "id", 1L);
    }

    // Builds a valid request; each test changes only what it needs.
    private EventRequest request(MatchFormat format, Integer changeEndsAt, OffsetDateTime endsAt) {
        return new EventRequest(
                "  Friday Smash  ",
                "  Court Hub  ",
                START,
                endsAt,
                2,
                format,
                changeEndsAt,
                "   "
        );
    }

    @Test
    void shouldCreateEventForLoggedInOrganizer() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        // Pretend save() works by giving back the same object it received.
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = eventService.create(
                EMAIL, request(MatchFormat.SINGLE_GAME, 11, START.plusHours(3)));

        // Capture the Event that was passed to save() and inspect it.
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        Event saved = captor.getValue();

        assertEquals(organizer, saved.getOrganizer());
        assertEquals("Friday Smash", saved.getName());   // trimmed
        assertEquals("Court Hub", saved.getLocation());  // trimmed
        assertNull(saved.getDescription());              // blank -> null
        assertEquals(11, saved.getChangeEndsAt());
        assertEquals(1L, response.organizerId());
    }

    @Test
    void shouldRejectEventEndingBeforeItStarts() {
        assertThrows(IllegalArgumentException.class, () -> eventService.create(
                EMAIL, request(MatchFormat.SINGLE_GAME, null, START.minusHours(1))));

        // Validation fails first, so nothing is looked up or saved.
        verify(eventRepository, never()).save(any());
    }

    @Test
    void shouldRejectChangeEndsAtForBestOfThree() {
        assertThrows(IllegalArgumentException.class, () -> eventService.create(
                EMAIL, request(MatchFormat.BEST_OF_THREE, 11, START.plusHours(3))));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void shouldNotFindAnotherOrganizersEvent() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        // Event 99 exists, but not for organizer 1, so the query returns nothing.
        when(eventRepository.findByIdAndOrganizerId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> eventService.getMyEvent(EMAIL, 99L));
    }

    @Test
    void shouldUpdateOwnedEvent() {
        Event existing = new Event(organizer, "Old name", "Old place", START,
                START.plusHours(2), 1, MatchFormat.BEST_OF_THREE, null, null);

        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        when(eventRepository.findByIdAndOrganizerId(5L, 1L)).thenReturn(Optional.of(existing));

        EventResponse response = eventService.update(
                EMAIL, 5L, request(MatchFormat.SINGLE_GAME, 11, START.plusHours(3)));

        // The entity itself was changed (Hibernate would save it at commit).
        assertEquals("Friday Smash", existing.getName());
        assertEquals(2, existing.getCourtCount());
        assertEquals(MatchFormat.SINGLE_GAME, response.matchFormat());

        // update() never calls save(): dirty checking handles it.
        verify(eventRepository, never()).save(any());
    }

    @Test
    void shouldDeleteOwnedEvent() {
        Event existing = new Event(organizer, "Friday Smash", "Court Hub", START,
                START.plusHours(2), 2, MatchFormat.SINGLE_GAME, null, null);

        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        when(eventRepository.findByIdAndOrganizerId(5L, 1L)).thenReturn(Optional.of(existing));

        eventService.delete(EMAIL, 5L);

        verify(eventRepository).delete(existing);
    }

    // Builds an ACTIVE event owned by our test organizer, and makes the fake
    // "database" return it as event 5.
    private Event givenOwnedEvent() {
        Event existing = new Event(organizer, "Friday Smash", "Court Hub", START,
                START.plusHours(2), 2, MatchFormat.SINGLE_GAME, null, null);

        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        when(eventRepository.findByIdAndOrganizerId(5L, 1L)).thenReturn(Optional.of(existing));
        return existing;
    }

    @Test
    void shouldStartNewEventsAsActive() {
        Event event = new Event(organizer, "Friday Smash", "Court Hub", START,
                START.plusHours(2), 2, MatchFormat.SINGLE_GAME, null, null);

        assertEquals(EventStatus.ACTIVE, event.getStatus());
    }

    @Test
    void shouldCompleteEvent() {
        Event existing = givenOwnedEvent();

        EventResponse response = eventService.complete(EMAIL, 5L);

        assertEquals(EventStatus.COMPLETED, existing.getStatus());
        assertEquals(EventStatus.COMPLETED, response.status());
    }

    @Test
    void shouldCancelEvent() {
        Event existing = givenOwnedEvent();

        EventResponse response = eventService.cancel(EMAIL, 5L);

        assertEquals(EventStatus.CANCELLED, existing.getStatus());
        assertEquals(EventStatus.CANCELLED, response.status());
    }

    @Test
    void shouldRejectCancellingCompletedEvent() {
        Event existing = givenOwnedEvent();
        existing.complete();

        assertThrows(InvalidStateException.class, () -> eventService.cancel(EMAIL, 5L));
        assertEquals(EventStatus.COMPLETED, existing.getStatus());
    }

    @Test
    void shouldRejectCompletingCancelledEvent() {
        Event existing = givenOwnedEvent();
        existing.cancel();

        assertThrows(InvalidStateException.class, () -> eventService.complete(EMAIL, 5L));
        assertEquals(EventStatus.CANCELLED, existing.getStatus());
    }

    @Test
    void shouldRejectUpdatingCompletedEvent() {
        Event existing = givenOwnedEvent();
        existing.complete();

        // The request tries to change the match format and the end time...
        assertThrows(InvalidStateException.class, () -> eventService.update(
                EMAIL, 5L, request(MatchFormat.BEST_OF_THREE, null, START.plusHours(3))));

        // ...but nothing changed.
        assertEquals(MatchFormat.SINGLE_GAME, existing.getMatchFormat());
        assertEquals(START.plusHours(2), existing.getEndsAt());
    }

    @Test
    void shouldReactivateCompletedEvent() {
        Event existing = givenOwnedEvent();
        existing.complete();

        EventResponse response = eventService.reactivate(EMAIL, 5L);

        assertEquals(EventStatus.ACTIVE, response.status());
    }

    @Test
    void shouldRejectReactivatingActiveEvent() {
        givenOwnedEvent();

        assertThrows(InvalidStateException.class, () -> eventService.reactivate(EMAIL, 5L));
    }

    @Test
    void shouldListActiveEventsSoonestFirst() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        // List.of() = an empty list; we only care HOW the repository was called.
        when(eventRepository.findByOrganizerIdAndStatus(any(), any(), any())).thenReturn(List.of());

        eventService.listMyEvents(EMAIL, EventStatus.ACTIVE);

        // Upcoming events: earliest start time first.
        verify(eventRepository).findByOrganizerIdAndStatus(
                1L, EventStatus.ACTIVE, Sort.by(Sort.Direction.ASC, "startsAt"));
    }

    @Test
    void shouldListCompletedEventsMostRecentFirst() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(organizer));
        when(eventRepository.findByOrganizerIdAndStatus(any(), any(), any())).thenReturn(List.of());

        eventService.listMyEvents(EMAIL, EventStatus.COMPLETED);

        // History: latest start time first.
        verify(eventRepository).findByOrganizerIdAndStatus(
                1L, EventStatus.COMPLETED, Sort.by(Sort.Direction.DESC, "startsAt"));
    }
}