package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.ParticipantRequest;
import com.smashit_beta.backend.dto.response.ParticipantResponse;
import com.smashit_beta.backend.service.ParticipantService;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// Participants are "nested" under their event in the URL, because a participant
// only exists inside one event: /api/events/{eventId}/participants/{participantId}
// The {eventId} placeholder in @RequestMapping is available to every method below.
@RestController
@RequestMapping("/api/events/{eventId}/participants")
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    // GET /api/events/5/participants -> the event's current participants (A to Z).
    @GetMapping
    public List<ParticipantResponse> list(
            Authentication authentication,
            @PathVariable Long eventId
    ) {
        return participantService.list(authentication.getName(), eventId);
    }

    // POST /api/events/5/participants  { "playerName": "Juan" } -> 201 Created.
    // Re-adding a removed participant restores them (same id, history kept).
    @PostMapping
    public ResponseEntity<ParticipantResponse> add(
            Authentication authentication,
            @PathVariable Long eventId,
            @Valid @RequestBody ParticipantRequest request
    ) {
        ParticipantResponse added = participantService.add(authentication.getName(), eventId, request);

        // e.g. Location: http://localhost:8080/api/events/5/participants/12
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(added.id())
                .toUri();

        return ResponseEntity.created(location).body(added);
    }

    // PUT /api/events/5/participants/12  { "playerName": "Juan D." } -> renamed participant.
    @PutMapping("/{participantId}")
    public ParticipantResponse rename(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long participantId,
            @Valid @RequestBody ParticipantRequest request
    ) {
        return participantService.rename(authentication.getName(), eventId, participantId, request);
    }

    // DELETE /api/events/5/participants/12 -> 204 No Content.
    // A soft delete: they disappear from the list, but their history stays.
    @DeleteMapping("/{participantId}")
    public ResponseEntity<Void> remove(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long participantId
    ) {
        participantService.remove(authentication.getName(), eventId, participantId);
        return ResponseEntity.noContent().build();
    }
}