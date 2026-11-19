package com.smashit_beta.backend.dto.request;

import com.smashit_beta.backend.entity.ShareRounding;

import jakarta.validation.constraints.NotNull;

// NEW (V11): { "shareRounding": "PESO_1" }  (NONE, PESO_1, PESO_5 or PESO_10)
public record ShareRoundingRequest(

        @NotNull(message = "Choose a rounding.")
        ShareRounding shareRounding
) {
}