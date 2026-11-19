package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.ContributionRequest;
import com.smashit_beta.backend.dto.response.ContributionResponse;
import com.smashit_beta.backend.service.ContributionService;

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

// NEW (V11): /api/events/{eventId}/contributions
@RestController
@RequestMapping("/api/events/{eventId}/contributions")
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    // GET /api/events/5/contributions -> every contribution, oldest first.
    @GetMapping
    public List<ContributionResponse> list(Authentication authentication, @PathVariable Long eventId) {
        return contributionService.list(authentication.getName(), eventId);
    }

    // POST /api/events/5/contributions -> 201 Created + Location header.
    @PostMapping
    public ResponseEntity<ContributionResponse> create(
            Authentication authentication,
            @PathVariable Long eventId,
            @Valid @RequestBody ContributionRequest request
    ) {
        ContributionResponse contribution = contributionService.create(authentication.getName(), eventId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(contribution.id())
                .toUri();

        return ResponseEntity.created(location).body(contribution);
    }

    // PUT /api/events/5/contributions/3 -> edit a contribution.
    @PutMapping("/{contributionId}")
    public ContributionResponse update(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long contributionId,
            @Valid @RequestBody ContributionRequest request
    ) {
        return contributionService.update(authentication.getName(), eventId, contributionId, request);
    }

    // DELETE /api/events/5/contributions/3 -> 204 No Content.
    @DeleteMapping("/{contributionId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long contributionId
    ) {
        contributionService.delete(authentication.getName(), eventId, contributionId);
        return ResponseEntity.noContent().build();
    }
}