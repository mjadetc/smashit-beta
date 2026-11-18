package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.MatchGame;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Points are saved and deleted through MatchGame (cascade), so they need no repository.
public interface MatchGameRepository extends JpaRepository<MatchGame, Long> {

    // The match's games in order: game 1, game 2, game 3.
    List<MatchGame> findByMatchIdOrderByGameNumberAsc(Long matchId);
}