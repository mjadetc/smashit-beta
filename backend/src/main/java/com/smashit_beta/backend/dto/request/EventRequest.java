package com.smashit_beta.backend.dto.request;

import com.smashit_beta.backend.entity.MatchFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

// The JSON body for BOTH creating (POST) and updating (PUT) an event.
// PUT replaces the whole event, so it needs the same fields as POST.
//
// Example:
// {
//   "name": "Friday Night Smash",
//   "location": "Court Hub, Quezon City",
//   "startsAt": "2026-11-20T19:00:00+08:00",
//   "endsAt":   "2026-11-20T22:00:00+08:00",
//   "courtCount": 2,
//   "matchFormat": "SINGLE_GAME",
//   "changeEndsAt": 11,
//   "description": "Bring your own racket."
// }
public record EventRequest(

        @NotBlank(message = "Event name is required.")
        @Size(max = 120, message = "Event name must not exceed 120 characters.")
        String name,

        @NotBlank(message = "Location is required.")
        @Size(max = 255, message = "Location must not exceed 255 characters.")
        String location,

        // @NotNull (not @NotBlank) because these aren't text.
        @NotNull(message = "Start time is required.")
        OffsetDateTime startsAt,

        @NotNull(message = "End time is required.")
        OffsetDateTime endsAt,

        // Integer (not int) so a missing value arrives as null and @NotNull catches it.
        // With int, a missing value would silently become 0.
        @NotNull(message = "Number of courts is required.")
        @Min(value = 1, message = "There must be at least 1 court.")
        @Max(value = 50, message = "There can be at most 50 courts.")
        Integer courtCount,

        // Spring converts the JSON text "SINGLE_GAME" into MatchFormat.SINGLE_GAME.
        // Any other text fails before validation even runs (see GlobalExceptionHandler).
        @NotNull(message = "Match format is required.")
        MatchFormat matchFormat,

        // Optional. Rules that involve two fields at once (e.g. "only for SINGLE_GAME",
        // "end after start") are checked in EventService instead.
        @Min(value = 1, message = "Change of ends must be at least 1 point.")
        @Max(value = 29, message = "Change of ends must be at most 29 points.")
        Integer changeEndsAt,

        @Size(max = 2000, message = "Description must not exceed 2000 characters.")
        String description
) {
}