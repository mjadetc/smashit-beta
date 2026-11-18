package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.EventRequest;
import com.smashit_beta.backend.dto.response.EventResponse;
import com.smashit_beta.backend.entity.EventStatus;
import com.smashit_beta.backend.service.EventService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// REST endpoints for events. "REST" style means: the URL names the thing (/api/events/5),
// and the HTTP method says what to do with it (GET = read, POST = create,
// PUT = replace, DELETE = remove).
//
// Every endpoint here requires login (SecurityConfig: anyRequest().authenticated()).
// The controller stays thin: it reads the HTTP request, calls EventService, and
// returns the result. The business rules live in the service.
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // POST /api/events -> create an event; returns 201 Created.
    // "Authentication authentication" = the logged-in user, supplied by Spring Security.
    // authentication.getName() is their email (see UserDetailsServiceImpl).
    @PostMapping
    public ResponseEntity<EventResponse> create(
            Authentication authentication,
            @Valid @RequestBody EventRequest request
    ) {
        EventResponse created = eventService.create(authentication.getName(), request);

        // Build the new event's URL, e.g. http://localhost:8080/api/events/5,
        // and send it in the "Location" header (a REST convention for 201 responses).
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // CHANGED: GET /api/events?status=... -> the logged-in organizer's events.
    //   GET /api/events                    -> ACTIVE events (the default)
    //   GET /api/events?status=COMPLETED   -> completed events
    //   GET /api/events?status=CANCELLED   -> cancelled events
    // @RequestParam reads "?status=..." from the URL and converts the text to the enum.
    // defaultValue is used when the parameter is missing.
    @GetMapping
    public List<EventResponse> listMyEvents(
            Authentication authentication,
            @RequestParam(defaultValue = "ACTIVE") EventStatus status
    ) {
        return eventService.listMyEvents(authentication.getName(), status);
    }

    // GET /api/events/5 -> one event.
    // @PathVariable copies the {eventId} part of the URL into the parameter.
    @GetMapping("/{eventId}")
    public EventResponse getMyEvent(
            Authentication authentication,
            @PathVariable Long eventId
    ) {
        return eventService.getMyEvent(authentication.getName(), eventId);
    }

    // PUT /api/events/5 -> replace the event's details.
    @PutMapping("/{eventId}")
    public EventResponse update(
            Authentication authentication,
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequest request
    ) {
        return eventService.update(authentication.getName(), eventId, request);
    }

    // NEW: POST /api/events/5/complete -> the event with status COMPLETED.
    // An "action" URL like /complete is a common REST style for state changes,
    // and is clearer than sending { "status": "COMPLETED" } in a PUT.
    @PostMapping("/{eventId}/complete")
    public EventResponse complete(
            Authentication authentication,
            @PathVariable Long eventId
    ) {
        return eventService.complete(authentication.getName(), eventId);
    }

    // NEW: POST /api/events/5/cancel -> the event with status CANCELLED.
    @PostMapping("/{eventId}/cancel")
    public EventResponse cancel(
            Authentication authentication,
            @PathVariable Long eventId
    ) {
        return eventService.cancel(authentication.getName(), eventId);
    }

    // NEW: POST /api/events/5/reactivate -> the event with status ACTIVE again.
    @PostMapping("/{eventId}/reactivate")
    public EventResponse reactivate(
            Authentication authentication,
            @PathVariable Long eventId
    ) {
        return eventService.reactivate(authentication.getName(), eventId);
    }

    // DELETE /api/events/5 -> 204 No Content. This is "remove": the event is gone for good.
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long eventId
    ) {
        eventService.delete(authentication.getName(), eventId);
        return ResponseEntity.noContent().build();
    }
}