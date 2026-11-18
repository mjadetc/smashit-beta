package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.entity.MatchStatus;
import com.smashit_beta.backend.repository.MatchPlayerRepository;
import com.smashit_beta.backend.repository.MatchPlayerRepository.ParticipantMatchStats;
import com.smashit_beta.backend.support.AuthTestHelper;
import com.smashit_beta.backend.support.AuthTestHelper.LoggedInUser;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for /api/events/{eventId}/matches and /queue, against the real database.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MatchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Plain SQL, to fake "this match was completed" until the scoring feature exists.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Hibernate's "persistence context" (its in-memory cache of loaded entities).
    @Autowired
    private EntityManager entityManager;

    // The real repository, to check the counting query directly.
    @Autowired
    private MatchPlayerRepository matchPlayerRepository;

    // ---------- helpers ----------

    // Creates an event with this many courts and returns its id.
    private Long createEvent(LoggedInUser user, int courtCount) throws Exception {
        String body = mockMvc.perform(user.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Friday Smash",
                                  "location": "Court Hub",
                                  "startsAt": "2026-11-20T19:00:00+08:00",
                                  "endsAt": "2026-11-20T22:00:00+08:00",
                                  "courtCount": %d,
                                  "matchFormat": "SINGLE_GAME"
                                }
                                """.formatted(courtCount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return idOf(body);
    }

    // Adds participants P1, P2, ... and returns their ids in that order.
    private List<Long> addParticipants(LoggedInUser user, Long eventId, int count) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String body = mockMvc.perform(user.authorize(post("/api/events/" + eventId + "/participants"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"playerName\": \"P" + i + "\" }"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            ids.add(idOf(body));
        }
        return ids;
    }

    private String matchesUrl(Long eventId) {
        return "/api/events/" + eventId + "/matches";
    }

    // Creates a match from a JSON body, checks 201, and returns the JSON response.
    private String createMatch(LoggedInUser user, Long eventId, String json) throws Exception {
        return mockMvc.perform(user.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // Every participant id seated in this match's JSON.
    private List<Long> seatedIds(String matchJson) {
        List<Number> teamA = JsonPath.read(matchJson, "$.teamA[*].participantId");
        List<Number> teamB = JsonPath.read(matchJson, "$.teamB[*].participantId");
        List<Long> ids = new ArrayList<>();
        teamA.forEach(id -> ids.add(id.longValue()));
        teamB.forEach(id -> ids.add(id.longValue()));
        return ids;
    }

    // Pretends the match was played to the end (the scoring feature will do this for real).
    private void completeMatchWithSql(Long matchId) {
        // Send Hibernate's pending changes to the database first...
        entityManager.flush();
        jdbcTemplate.update("""
                UPDATE matches
                SET status = 'COMPLETED', winning_team = 'A', started_at = NOW(), completed_at = NOW()
                WHERE id = ?
                """, matchId);
        // ...then empty Hibernate's cache, so it re-reads the row we just changed behind its back.
        entityManager.clear();
    }

    // ---------- tests ----------

    @Test
    void shouldFillAllSeatsWhenNobodyIsChosen() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 2);
        addParticipants(organizer, eventId, 6);

        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1 }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courtNumber").value(1))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.teamA", hasSize(2)))
                .andExpect(jsonPath("$.teamB", hasSize(2)))
                .andExpect(jsonPath("$.teamA[0].autoAssigned").value(true))
                .andExpect(jsonPath("$.teamB[1].autoAssigned").value(true));
    }

    @Test
    void shouldKeepChosenPairAndFillTheOtherTeam() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);
        List<Long> ids = addParticipants(organizer, eventId, 6);

        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1, \"teamA\": [%d, %d] }".formatted(ids.get(0), ids.get(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamA[0].participantId").value(ids.get(0)))
                .andExpect(jsonPath("$.teamA[0].playerName").value("P1"))
                .andExpect(jsonPath("$.teamA[0].autoAssigned").value(false))
                .andExpect(jsonPath("$.teamA[1].participantId").value(ids.get(1)))
                .andExpect(jsonPath("$.teamB", hasSize(2)))
                .andExpect(jsonPath("$.teamB[0].autoAssigned").value(true));
    }

    // The scenario that replaced rounds: court 1 finishes early and starts its next
    // match while court 2 is still playing.
    @Test
    void shouldStartNextMatchOnFreeCourtWhileOtherCourtIsStillPlaying() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 2);
        List<Long> ids = addParticipants(organizer, eventId, 10);

        String court1Json = createMatch(organizer, eventId, "{ \"courtNumber\": 1 }");
        String court2Json = createMatch(organizer, eventId, "{ \"courtNumber\": 2 }");
        Long court2 = idOf(court2Json);
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId) + "/" + court2 + "/start")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Court 1 is done; court 2 is still IN_PROGRESS.
        completeMatchWithSql(idOf(court1Json));

        List<Long> seated = seatedIds(createMatch(organizer, eventId, "{ \"courtNumber\": 1 }"));

        // Nobody from court 2 may be picked: they're still playing (FR-017).
        List<Long> onCourt2 = seatedIds(court2Json);
        onCourt2.forEach(id -> assertTrue(!seated.contains(id), "Court 2 players are busy"));

        // The 2 players who haven't played yet must be in it (FR-015, FR-019).
        List<Long> neverPlayed = new ArrayList<>(ids);
        neverPlayed.removeAll(seatedIds(court1Json));
        neverPlayed.removeAll(onCourt2);
        assertEquals(2, neverPlayed.size());
        assertTrue(seated.containsAll(neverPlayed));

        mockMvc.perform(organizer.authorize(get(matchesUrl(eventId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void shouldRejectSecondMatchOnBusyCourt() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 2);
        addParticipants(organizer, eventId, 8);
        createMatch(organizer, eventId, "{ \"courtNumber\": 1 }");

        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1 }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Court 1 already has a match that hasn't finished."));
    }

    @Test
    void shouldPutPlayersWhoJustPlayedAtTheBackOfTheQueue() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);
        List<Long> ids = addParticipants(organizer, eventId, 6);

        // P1..P4 play and finish; P5 and P6 wait.
        Long first = idOf(createMatch(organizer, eventId, "{ \"courtNumber\": 1, \"teamA\": [%d, %d], \"teamB\": [%d, %d] }"
                .formatted(ids.get(0), ids.get(1), ids.get(2), ids.get(3))));
        completeMatchWithSql(first);

        // The queue shows the two who haven't played first.
        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId + "/queue")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].playerName").value("P5"))
                .andExpect(jsonPath("$[0].completedMatches").value(0))
                .andExpect(jsonPath("$[1].playerName").value("P6"))
                .andExpect(jsonPath("$[2].completedMatches").value(1));

        // FR-015 / FR-019: the next automatic match must include P5 and P6.
        List<Long> seated = seatedIds(createMatch(organizer, eventId, "{ \"courtNumber\": 1 }"));
        assertTrue(seated.containsAll(List.of(ids.get(4), ids.get(5))));
    }

    @Test
    void shouldReshuffleOnlyTheSeatsTheAppFilled() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);
        List<Long> ids = addParticipants(organizer, eventId, 8);
        Long matchId = idOf(createMatch(organizer, eventId,
                "{ \"courtNumber\": 1, \"teamA\": [%d, %d] }".formatted(ids.get(0), ids.get(1))));

        // However many times we reshuffle, the organizer's pair stays on team A.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(organizer.authorize(post(matchesUrl(eventId) + "/" + matchId + "/reshuffle")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(matchId))
                    .andExpect(jsonPath("$.teamA[0].participantId").value(ids.get(0)))
                    .andExpect(jsonPath("$.teamA[1].participantId").value(ids.get(1)))
                    .andExpect(jsonPath("$.teamA[0].autoAssigned").value(false))
                    .andExpect(jsonPath("$.teamB", hasSize(2)))
                    .andExpect(jsonPath("$.teamB[0].autoAssigned").value(true));
        }
    }

    @Test
    void shouldChangeLineupBeforeStartButNotAfter() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);
        List<Long> ids = addParticipants(organizer, eventId, 6);
        Long matchId = idOf(createMatch(organizer, eventId,
                "{ \"courtNumber\": 1, \"teamA\": [%d, %d] }".formatted(ids.get(0), ids.get(1))));
        String lineupUrl = matchesUrl(eventId) + "/" + matchId + "/lineup";

        // Plans changed: P1 now partners P5, and P2 is on the other side.
        mockMvc.perform(organizer.authorize(put(lineupUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"teamA\": [%d, %d], \"teamB\": [%d] }"
                                .formatted(ids.get(0), ids.get(4), ids.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamA[1].participantId").value(ids.get(4)))
                .andExpect(jsonPath("$.teamB[0].participantId").value(ids.get(1)))
                .andExpect(jsonPath("$.teamB[1].autoAssigned").value(true));

        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId) + "/" + matchId + "/start")))
                .andExpect(status().isOk());

        // Once started, the line-up is locked.
        mockMvc.perform(organizer.authorize(put(lineupUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"teamA\": [%d] }".formatted(ids.get(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The line-up can only be changed before the match starts."));
    }

    @Test
    void shouldOnlyCountCompletedMatches() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);
        addParticipants(organizer, eventId, 4);

        // Match 1 is completed: 4 players get 1 completed match each.
        completeMatchWithSql(idOf(createMatch(organizer, eventId, "{ \"courtNumber\": 1 }")));

        // Match 2 is cancelled: it must NOT count (FR-020, FR-035).
        Long second = idOf(createMatch(organizer, eventId, "{ \"courtNumber\": 1 }"));
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId) + "/" + second + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        List<ParticipantMatchStats> stats =
                matchPlayerRepository.findStatsPerParticipant(eventId, MatchStatus.COMPLETED);

        // 4 players with 1 match each. (A total of 8 would mean the cancelled one counted.)
        long total = stats.stream().mapToLong(ParticipantMatchStats::getMatchCount).sum();
        assertEquals(4, stats.size());
        assertEquals(4, total);
    }

    @Test
    void shouldRejectMatchWhenNotEnoughFreePlayers() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 2);
        addParticipants(organizer, eventId, 6);
        createMatch(organizer, eventId, "{ \"courtNumber\": 1 }");

        // Only 2 players are left for court 2.
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 2 }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Not enough free players to fill the court: 4 needed, 2 available."));
    }

    @Test
    void shouldValidateTheRequestBody() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);

        // No court number.
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courtNumber").value("Court number is required."));

        // Three players on one team.
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1, \"teamA\": [1, 2, 3] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.teamA").value("A team has at most 2 players."));

        // Court 2 doesn't exist in a 1-court event.
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 2 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Court number must be between 1 and 1."));
    }

    @Test
    void shouldNotRemoveParticipantWhoIsOnCourt() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, 1);
        List<Long> ids = addParticipants(organizer, eventId, 4);
        Long matchId = idOf(createMatch(organizer, eventId, "{ \"courtNumber\": 1 }"));
        String removeP1 = "/api/events/" + eventId + "/participants/" + ids.get(0);

        mockMvc.perform(organizer.authorize(delete(removeP1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "\"P1\" is in a match that hasn't finished. Change that match's line-up or cancel it first."));

        // After cancelling the match, removing works.
        mockMvc.perform(organizer.authorize(post(matchesUrl(eventId) + "/" + matchId + "/cancel")))
                .andExpect(status().isOk());
        mockMvc.perform(organizer.authorize(delete(removeP1)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldNotLetAnotherOrganizerSeeOrCreateMatches() throws Exception {
        LoggedInUser owner = AuthTestHelper.registerAndLogin(mockMvc);
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(owner, 1);
        addParticipants(owner, eventId, 4);
        Long matchId = idOf(createMatch(owner, eventId, "{ \"courtNumber\": 1 }"));

        mockMvc.perform(intruder.authorize(get(matchesUrl(eventId))))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(get("/api/events/" + eventId + "/queue")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(post(matchesUrl(eventId) + "/" + matchId + "/cancel")))
                .andExpect(status().isNotFound());
    }
}