package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.MatchGame;
import com.smashit_beta.backend.entity.MatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Points are saved and deleted through MatchGame (cascade), so they need no repository.
public interface MatchGameRepository extends JpaRepository<MatchGame, Long> {

    // The match's games in order: game 1, game 2, game 3.
    List<MatchGame> findByMatchIdOrderByGameNumberAsc(Long matchId);

    // Every game of the event's matches with this status (e.g. COMPLETED), in ONE query
    // instead of one query per match. "MatchEventId" = the match field, its event, then its id.
    List<MatchGame> findByMatchEventIdAndMatchStatusOrderByGameNumberAsc(Long eventId, MatchStatus status);
}