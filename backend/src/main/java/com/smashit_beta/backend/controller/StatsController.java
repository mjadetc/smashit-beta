package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.response.EventSummaryResponse;
import com.smashit_beta.backend.dto.response.PlayerStatsResponse;
import com.smashit_beta.backend.service.StatsService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only statistics for one event. Works for any event status, so a finished
// event's results can still be reviewed.
@RestController
@RequestMapping("/api/events/{eventId}")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    // GET /api/events/5/player-stats -> standings: matches, wins, games, points per player.
    @GetMapping("/player-stats")
    public List<PlayerStatsResponse> playerStats(Authentication authentication, @PathVariable Long eventId) {
        return statsService.playerStats(authentication.getName(), eventId);
    }

    // GET /api/events/5/summary -> match counts, durations, courts, recent results.
    @GetMapping("/summary")
    public EventSummaryResponse summary(Authentication authentication, @PathVariable Long eventId) {
        return statsService.summary(authentication.getName(), eventId);
    }
}