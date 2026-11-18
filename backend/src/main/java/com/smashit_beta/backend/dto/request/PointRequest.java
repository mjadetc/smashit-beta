package com.smashit_beta.backend.dto.request;

import com.smashit_beta.backend.entity.Team;

import jakarta.validation.constraints.NotNull;

// FR-029: "this team won the rally".  { "team": "B" }
public record PointRequest(

        @NotNull(message = "Choose the team that won the rally.")
        Team team
) {
}