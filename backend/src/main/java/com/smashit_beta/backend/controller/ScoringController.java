package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.PointRequest;
import com.smashit_beta.backend.dto.request.StartGameRequest;
import com.smashit_beta.backend.dto.response.ScoreboardResponse;
import com.smashit_beta.backend.service.ScoringService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Scoring one match on the court screen: /api/events/{eventId}/matches/{matchId}/...
// Every endpoint returns the whole scoreboard, so the UI redraws from one response.
@RestController
@RequestMapping("/api/events/{eventId}/matches/{matchId}")
public class ScoringController {

    private final ScoringService scoringService;

    public ScoringController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    // GET .../scoreboard -> score, server, positions, games, next step.
    @GetMapping("/scoreboard")
    public ScoreboardResponse scoreboard(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return scoringService.scoreboard(authentication.getName(), eventId, matchId);
    }

    // POST .../games  { "servingTeam": "A", "serverId": 12, "receiverId": 20 } -> next game starts.
    @PostMapping("/games")
    public ScoreboardResponse startGame(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId,
            @Valid @RequestBody StartGameRequest request
    ) {
        return scoringService.startGame(authentication.getName(), eventId, matchId, request);
    }

    // POST .../points  { "team": "B" } -> team B won the rally.
    @PostMapping("/points")
    public ScoreboardResponse recordPoint(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId,
            @Valid @RequestBody PointRequest request
    ) {
        return scoringService.recordPoint(authentication.getName(), eventId, matchId, request);
    }

    // POST .../undo -> removes the latest rally (or an untouched "start game").
    @PostMapping("/undo")
    public ScoreboardResponse undo(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return scoringService.undo(authentication.getName(), eventId, matchId);
    }

    // POST .../complete -> confirm the result; the match becomes COMPLETED.
    @PostMapping("/complete")
    public ScoreboardResponse complete(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long matchId
    ) {
        return scoringService.completeMatch(authentication.getName(), eventId, matchId);
    }
}