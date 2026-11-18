package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.CreateMatchRequest;
import com.smashit_beta.backend.dto.request.LineupRequest;
import com.smashit_beta.backend.dto.response.MatchResponse;
import com.smashit_beta.backend.dto.response.QueueEntryResponse;
import com.smashit_beta.backend.service.MatchService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

// Matches live under their event: /api/events/{eventId}/matches/{matchId}
// (The "next up" queue is at /api/events/{eventId}/queue, see the last method.)
@RestController
@RequestMapping("/api/events/{eventId}")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    // GET /api/events/5/matches -> every match of the event, oldest first.
    @GetMapping("/matches")
    public List<MatchResponse> list(Authentication authentication, @PathVariable Long eventId) {
        return matchService.list(authentication.getName(), eventId);
    }

    // GET /api/events/5/matches/40 -> one match.
    @GetMapping("/matches/{matchId}")
    public MatchResponse get(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return matchService.get(authentication.getName(), eventId, matchId);
    }

    // POST /api/events/5/matches  { "courtNumber": 1, "teamA": [12, 15] } -> 201 Created.
    @PostMapping("/matches")
    public ResponseEntity<MatchResponse> create(
            Authentication authentication,
            @PathVariable Long eventId,
            @Valid @RequestBody CreateMatchRequest request
    ) {
        MatchResponse match = matchService.create(authentication.getName(), eventId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(match.id())
                .toUri();

        return ResponseEntity.created(location).body(match);
    }

    // PUT /api/events/5/matches/40/lineup  { "teamA": [12, 30], "teamB": [20] }
    // -> new line-up for a SCHEDULED match (empty seats filled by the app).
    @PutMapping("/matches/{matchId}/lineup")
    public MatchResponse updateLineup(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId,
            @Valid @RequestBody LineupRequest request
    ) {
        return matchService.updateLineup(authentication.getName(), eventId, matchId, request);
    }

    // POST /api/events/5/matches/40/reshuffle -> re-pick only the seats the app filled.
    @PostMapping("/matches/{matchId}/reshuffle")
    public MatchResponse reshuffle(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return matchService.reshuffle(authentication.getName(), eventId, matchId);
    }

    // POST /api/events/5/matches/40/start -> SCHEDULED becomes IN_PROGRESS.
    @PostMapping("/matches/{matchId}/start")
    public MatchResponse start(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return matchService.start(authentication.getName(), eventId, matchId);
    }

    // POST /api/events/5/matches/40/cancel -> CANCELLED; frees the court and players.
    @PostMapping("/matches/{matchId}/cancel")
    public MatchResponse cancel(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return matchService.cancel(authentication.getName(), eventId, matchId);
    }

    // GET /api/events/5/queue -> free players, fairest first ("next up").
    @GetMapping("/queue")
    public List<QueueEntryResponse> queue(Authentication authentication, @PathVariable Long eventId) {
        return matchService.queue(authentication.getName(), eventId);
    }
}