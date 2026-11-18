package com.smashit_beta.backend.dto.response;

import com.smashit_beta.backend.entity.Event;
import com.smashit_beta.backend.entity.EventStatus;
import com.smashit_beta.backend.entity.MatchFormat;

import java.time.Instant;
import java.time.OffsetDateTime;

// What the frontend receives for an event.
// We return organizerId instead of the whole User, so the organizer's
// email and password hash are never included by accident.
public record EventResponse(
        Long id,
        Long organizerId,
        String name,
        String location,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int courtCount,
        MatchFormat matchFormat,
        Integer changeEndsAt,
        String description,
        // NEW: ACTIVE, COMPLETED or CANCELLED, so the frontend can show a badge.
        EventStatus status,
        Instant createdAt
) {

    // Converts an Event entity into the response shape (same idea as UserResponse.from).
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                // Only reads the id, so Hibernate doesn't need to load the lazy User.
                event.getOrganizer().getId(),
                event.getName(),
                event.getLocation(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCourtCount(),
                event.getMatchFormat(),
                event.getChangeEndsAt(),
                event.getDescription(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }
}