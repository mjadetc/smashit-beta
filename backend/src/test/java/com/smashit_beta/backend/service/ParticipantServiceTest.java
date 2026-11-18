package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.ParticipantRequest;
import com.smashit_beta.backend.dto.response.ParticipantResponse;
import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventParticipant;
import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.User;
import com.smashit_beta.backend.exception.DuplicateResourceException;
import com.smashit_beta.backend.exception.InvalidStateException;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import com.smashit_beta.backend.repository.EventParticipantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UNIT tests for ParticipantService. Both dependencies are Mockito fakes:
// EventService (for "load my event") and the participant repository.
@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    private static final String EMAIL = "organizer@example.com";
    private static final Long EVENT_ID = 5L;

    @Mock
    private EventService eventService;

    @Mock
    private EventParticipantRepository participantRepository;

    @InjectMocks
    private ParticipantService participantService;

    private Event event;

    @BeforeEach
    void setUp() {
        User organizer = new User("Test", "Organizer", "organizer", EMAIL, "hash");
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 20, 19, 0, 0, 0, ZoneOffset.ofHours(8));

        event = new Event(organizer, "Friday Smash", "Court Hub", start, start.plusHours(3),
                2, MatchFormat.SINGLE_GAME, null, null);
        // The database normally assigns ids; tests set them directly.
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
    }

    // Makes the fake EventService return our event (i.e. "you own this event").
    private void givenOwnedEvent() {
        when(eventService.findOwnedEvent(EMAIL, EVENT_ID)).thenReturn(event);
    }

    // Builds a participant with an id, as if it had been loaded from the database.
    private EventParticipant participant(Long id, String name) {
        EventParticipant participant = new EventParticipant(event, name);
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }

    @Test
    void shouldAddParticipantWithCleanedUpName() {
        givenOwnedEvent();
        when(participantRepository.findByEventIdAndPlayerNameIgnoreCase(EVENT_ID, "Juan Dela Cruz"))
                .thenReturn(Optional.empty());
        when(participantRepository.save(any(EventParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        participantService.add(EMAIL, EVENT_ID, new ParticipantRequest("  Juan   Dela  Cruz "));

        ArgumentCaptor<EventParticipant> captor = ArgumentCaptor.forClass(EventParticipant.class);
        verify(participantRepository).save(captor.capture());

        // Extra spaces were removed, and the participant starts active.
        assertEquals("Juan Dela Cruz", captor.getValue().getPlayerName());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void shouldRejectDuplicateActiveName() {
        givenOwnedEvent();
        when(participantRepository.findByEventIdAndPlayerNameIgnoreCase(EVENT_ID, "juan"))
                .thenReturn(Optional.of(participant(10L, "Juan")));

        assertThrows(DuplicateResourceException.class,
                () -> participantService.add(EMAIL, EVENT_ID, new ParticipantRequest("juan")));

        verify(participantRepository, never()).save(any());
    }

    @Test
    void shouldRestoreRemovedParticipantInsteadOfCreatingNewOne() {
        givenOwnedEvent();
        EventParticipant removed = participant(10L, "juan");
        removed.remove();
        when(participantRepository.findByEventIdAndPlayerNameIgnoreCase(EVENT_ID, "Juan"))
                .thenReturn(Optional.of(removed));

        ParticipantResponse response =
                participantService.add(EMAIL, EVENT_ID, new ParticipantRequest("Juan"));

        // Same row (id 10) is back, with the new spelling.
        assertEquals(10L, response.id());
        assertEquals("Juan", response.playerName());
        assertTrue(response.active());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void shouldNotAddParticipantToCompletedEvent() {
        event.complete();
        givenOwnedEvent();

        assertThrows(InvalidStateException.class,
                () -> participantService.add(EMAIL, EVENT_ID, new ParticipantRequest("Juan")));
    }

    @Test
    void shouldRenameParticipant() {
        givenOwnedEvent();
        EventParticipant juan = participant(10L, "Juan");
        when(participantRepository.findByIdAndEventId(10L, EVENT_ID)).thenReturn(Optional.of(juan));
        when(participantRepository.existsByEventIdAndPlayerNameIgnoreCaseAndIdNot(EVENT_ID, "Juan D.", 10L))
                .thenReturn(false);

        ParticipantResponse response =
                participantService.rename(EMAIL, EVENT_ID, 10L, new ParticipantRequest("Juan D."));

        assertEquals("Juan D.", response.playerName());
        assertEquals("Juan D.", juan.getPlayerName());
    }

    @Test
    void shouldRejectRenameToAnotherParticipantsName() {
        givenOwnedEvent();
        when(participantRepository.findByIdAndEventId(10L, EVENT_ID))
                .thenReturn(Optional.of(participant(10L, "Juan")));
        when(participantRepository.existsByEventIdAndPlayerNameIgnoreCaseAndIdNot(EVENT_ID, "Maria", 10L))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> participantService.rename(EMAIL, EVENT_ID, 10L, new ParticipantRequest("Maria")));
    }

    @Test
    void shouldSoftDeleteParticipant() {
        givenOwnedEvent();
        EventParticipant juan = participant(10L, "Juan");
        when(participantRepository.findByIdAndEventId(10L, EVENT_ID)).thenReturn(Optional.of(juan));

        participantService.remove(EMAIL, EVENT_ID, 10L);

        // The row isn't deleted, just marked inactive.
        assertFalse(juan.isActive());
        verify(participantRepository, never()).delete(any());
    }

    @Test
    void shouldNotFindParticipantFromAnotherEvent() {
        givenOwnedEvent();
        // Participant 99 exists, but not in event 5.
        when(participantRepository.findByIdAndEventId(99L, EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> participantService.remove(EMAIL, EVENT_ID, 99L));
    }

    @Test
    void shouldNotRemoveAlreadyRemovedParticipant() {
        givenOwnedEvent();
        EventParticipant removed = participant(10L, "Juan");
        removed.remove();
        when(participantRepository.findByIdAndEventId(10L, EVENT_ID)).thenReturn(Optional.of(removed));

        assertThrows(ResourceNotFoundException.class,
                () -> participantService.remove(EMAIL, EVENT_ID, 10L));
    }
}