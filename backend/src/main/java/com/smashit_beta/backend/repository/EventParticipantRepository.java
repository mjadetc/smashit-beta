package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.EventParticipant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Spring Data writes the SQL for each method from its name.
// "EventId" means: the "event" field, then its "id".
public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    // The event's current (active) participants, alphabetical.
    // -> SELECT * FROM event_participants
    //    WHERE event_id = ? AND active = TRUE ORDER BY player_name ASC
    List<EventParticipant> findByEventIdAndActiveTrueOrderByPlayerNameAsc(Long eventId);

    // EVERYONE in the event, active or removed, alphabetical.
    // (Statistics still show a removed player's completed matches.)
    List<EventParticipant> findByEventIdOrderByPlayerNameAsc(Long eventId);

    // A participant, but only if it belongs to THIS event.
    // A participant id from a different event gives "empty" -> 404.
    Optional<EventParticipant> findByIdAndEventId(Long id, Long eventId);

    // Anyone in this event (active OR removed) with this name, ignoring case.
    // Used when adding: an existing removed "Juan" is restored instead of duplicated.
    Optional<EventParticipant> findByEventIdAndPlayerNameIgnoreCase(Long eventId, String playerName);

    // Does SOMEONE ELSE in this event already use this name? ("IdNot" = id <> ?)
    // Used when renaming, so a participant can keep their own name.
    boolean existsByEventIdAndPlayerNameIgnoreCaseAndIdNot(Long eventId, String playerName, Long id);
}