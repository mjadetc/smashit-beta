package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.Match;
import com.smashit_beta.backend.entity.MatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    // Every match of the event, oldest first (ids grow over time).
    List<Match> findByEventIdOrderByIdAsc(Long eventId);

    // The event's matches in these statuses, e.g. the open ones (SCHEDULED, IN_PROGRESS).
    // Used to find which courts and players are busy right now.
    List<Match> findByEventIdAndStatusIn(Long eventId, Collection<MatchStatus> statuses);

    // A match, but only if it belongs to THIS event (otherwise empty -> 404).
    Optional<Match> findByIdAndEventId(Long id, Long eventId);
}