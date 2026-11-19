package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.Expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // The event's expenses: earliest date first, then in the order they were added.
    List<Expense> findByEventIdOrderByExpenseDateAscIdAsc(Long eventId);

    // An expense, but only if it belongs to THIS event (otherwise empty -> 404).
    Optional<Expense> findByIdAndEventId(Long id, Long eventId);
}