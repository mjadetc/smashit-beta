package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventStatus;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// JpaRepository<Event, Long> gives us save, findById, delete, and more for free.
// Spring Data writes the SQL for the methods below by reading their NAMES.
public interface EventRepository extends JpaRepository<Event, Long> {

    // CHANGED: "my events", filtered by status.
    // "findBy OrganizerId And Status"
    //  -> SELECT * FROM events WHERE organizer_id = ? AND status = ? ORDER BY ...
    // "OrganizerId" means: the "organizer" field, then its "id".
    // The Sort parameter lets the SERVICE choose the order (it differs per status),
    // instead of hard-coding "OrderByStartsAtDesc" into the method name.
    List<Event> findByOrganizerIdAndStatus(Long organizerId, EventStatus status, Sort sort);

    // -> SELECT * FROM events WHERE id = ? AND organizer_id = ?
    // This is how we enforce ownership (FR-004, NFR-002): if the event belongs to
    // someone else, the result is simply empty, exactly as if it didn't exist.
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);
}