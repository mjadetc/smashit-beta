package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.ExpenseRequest;
import com.smashit_beta.backend.dto.response.ExpenseResponse;
import com.smashit_beta.backend.dto.response.ExpenseSummaryResponse;
import com.smashit_beta.backend.service.ExpenseService;

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

// Expenses live under their event: /api/events/{eventId}/expenses
@RestController
@RequestMapping("/api/events/{eventId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // GET /api/events/5/expenses -> every expense, earliest date first.
    @GetMapping
    public List<ExpenseResponse> list(Authentication authentication, @PathVariable Long eventId) {
        return expenseService.list(authentication.getName(), eventId);
    }

    // POST /api/events/5/expenses -> 201 Created + Location header.
    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            Authentication authentication,
            @PathVariable Long eventId,
            @Valid @RequestBody ExpenseRequest request
    ) {
        ExpenseResponse expense = expenseService.create(authentication.getName(), eventId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(expense.id())
                .toUri();

        return ResponseEntity.created(location).body(expense);
    }

    // PUT /api/events/5/expenses/9 -> edit an expense.
    @PutMapping("/{expenseId}")
    public ExpenseResponse update(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseRequest request
    ) {
        return expenseService.update(authentication.getName(), eventId, expenseId, request);
    }

    // DELETE /api/events/5/expenses/9 -> 204 No Content.
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long eventId,
            @PathVariable Long expenseId
    ) {
        expenseService.delete(authentication.getName(), eventId, expenseId);
        return ResponseEntity.noContent().build();
    }

    // GET /api/events/5/expenses/summary -> total, shares, balances, who pays whom.
    // ("/summary" doesn't clash with "/{expenseId}": that one is only for PUT and DELETE.)
    @GetMapping("/summary")
    public ExpenseSummaryResponse summary(Authentication authentication, @PathVariable Long eventId) {
        return expenseService.summary(authentication.getName(), eventId);
    }
}