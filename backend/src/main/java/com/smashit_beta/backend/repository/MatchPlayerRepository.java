package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.MatchPlayer;
import com.smashit_beta.backend.entity.MatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Long> {

    // A "projection": instead of whole entities, the query returns just these values per row.
    // Spring fills in each getter from the matching alias in the query ("AS participantId").
    interface ParticipantMatchStats {
        Long getParticipantId();

        // How many matches they finished.
        Long getMatchCount();

        // When their latest finished match ended (used for "longest waiting first").
        Instant getLastPlayedAt();
    }

    // FR-015 / FR-020: per participant, how many matches with this status they've played
    // in this event, and when the latest one ended. We always call it with COMPLETED,
    // so cancelled or unfinished matches never count.
    // We COUNT instead of storing a counter, so the number can never get out of sync.
    // Participants with zero matches simply don't appear in the result.
    // (JPQL: looks like SQL, but uses CLASS and FIELD names, not table and column names.)
    @Query("""
            SELECT mp.participant.id AS participantId,
                   COUNT(mp) AS matchCount,
                   MAX(mp.match.completedAt) AS lastPlayedAt
            FROM MatchPlayer mp
            WHERE mp.match.event.id = :eventId
              AND mp.match.status = :status
            GROUP BY mp.participant.id
            """)
    List<ParticipantMatchStats> findStatsPerParticipant(
            @Param("eventId") Long eventId,
            @Param("status") MatchStatus status
    );

    // "Is this participant in a match with one of these statuses?"
    // Used to stop removing someone who is scheduled or playing right now.
    // "MatchStatus" in the name = the "status" field of the "match" field.
    boolean existsByParticipantIdAndMatchStatusIn(Long participantId, Collection<MatchStatus> statuses);
}