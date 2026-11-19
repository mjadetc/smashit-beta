package com.smashit_beta.backend.dto.request;

// NEW (V11): does this participant pay an equal share?
//   { "sharesEqually": true }   yes
//   { "sharesEqually": false }  no (they pay only their contribution, if any)
//   { "sharesEqually": null }   back to the default rule (active or finished a match)
// No @NotNull on purpose: null is a valid choice here.
public record SharingRequest(Boolean sharesEqually) {
}