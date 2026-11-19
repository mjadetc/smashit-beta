package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.Contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    // The event's contributions, in the order they were added.
    List<Contribution> findByEventIdOrderByIdAsc(Long eventId);

    // A contribution, but only if it belongs to THIS event (otherwise empty -> 404).
    Optional<Contribution> findByIdAndEventId(Long id, Long eventId);

    // The contributions labelled "for" this expense.
    List<Contribution> findByExpenseId(Long expenseId);
}