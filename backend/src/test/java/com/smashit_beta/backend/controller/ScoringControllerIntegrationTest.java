package com.smashit_beta.backend.controller;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for scoring: /api/events/{eventId}/matches/{matchId}/...
// Every test uses one court with P1 + P2 (team A) against P3 + P4 (team B).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScoringControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // To check the stored rallies directly.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    // Filled in by setUpMatch(): the organizer, the ids, and the match's base URL.
    private LoggedInUser organizer;
    private Long eventId;
    private List<Long> players;
    private String matchUrl;

    // ---------- helpers ----------

    // An event with this format, 4 players, and one match: P1 + P2 vs P3 + P4.
    // startMatch = also press "Start match" (SCHEDULED -> IN_PROGRESS).
    private void setUpMatch(String matchFormat, Integer changeEndsAt, boolean startMatch) throws Exception {
        organizer = AuthTestHelper.registerAndLogin(mockMvc);

        String eventJson = mockMvc.perform(organizer.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Friday Smash",
                                  "location": "Court Hub",
                                  "startsAt": "2026-11-20T19:00:00+08:00",
                                  "endsAt": "2026-11-20T22:00:00+08:00",
                                  "courtCount": 1,
                                  "matchFormat": "%s",
                                  "changeEndsAt": %s
                                }
                                """.formatted(matchFormat, changeEndsAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        eventId = idOf(eventJson);

        players = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String body = mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/participants"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"playerName\": \"P" + i + "\" }"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            players.add(idOf(body));
        }

        String matchJson = mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/matches"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1, \"teamA\": [%d, %d], \"teamB\": [%d, %d] }"
                                .formatted(player(1), player(2), player(3), player(4))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        matchUrl = "/api/events/" + eventId + "/matches/" + idOf(matchJson);

        if (startMatch) {
            mockMvc.perform(organizer.authorize(post(matchUrl + "/start"))).andExpect(status().isOk());
        }
    }

    // The id of P1..P4.
    private Long player(int number) {
        return players.get(number - 1);
    }

    private Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // POST .../games and return the result, so the test can check it.
    private ResultActions startGame(String servingTeam, Long serverId, Long receiverId) throws Exception {
        return mockMvc.perform(organizer.authorize(post(matchUrl + "/games"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"servingTeam\": \"%s\", \"serverId\": %d, \"receiverId\": %d }"
                        .formatted(servingTeam, serverId, receiverId)));
    }

    // Records `count` rallies won by `team`, expecting each to succeed. Returns the last result.
    private ResultActions points(String team, int count) throws Exception {
        ResultActions last = null;
        for (int i = 0; i < count; i++) {
            last = mockMvc.perform(organizer.authorize(post(matchUrl + "/points"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"team\": \"" + team + "\" }"))
                    .andExpect(status().isOk());
        }
        return last;
    }

    private ResultActions undo() throws Exception {
        return mockMvc.perform(organizer.authorize(post(matchUrl + "/undo")));
    }

    // ---------- tests ----------

    @Test
    void shouldNotScoreMatchThatHasNotStarted() throws Exception {
        setUpMatch("SINGLE_GAME", null, false);

        mockMvc.perform(organizer.authorize(get(matchUrl + "/scoreboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("START_MATCH"))
                .andExpect(jsonPath("$.currentGame").value(nullValue()));

        startGame("A", player(1), player(3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Start the match before scoring it."));
    }

    @Test
    void shouldStartGameWithChosenServerAndReceiver() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);

        // FR-026: P2 serves to P4. Both stand in their RIGHT service courts.
        startGame("A", player(2), player(4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("RECORD_POINT"))
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.currentGame.gameNumber").value(1))
                .andExpect(jsonPath("$.currentGame.servingTeam").value("A"))
                .andExpect(jsonPath("$.currentGame.server.playerName").value("P2"))
                .andExpect(jsonPath("$.currentGame.receiver.playerName").value("P4"))
                .andExpect(jsonPath("$.currentGame.teamARightCourt.playerName").value("P2"))
                .andExpect(jsonPath("$.currentGame.teamALeftCourt.playerName").value("P1"))
                .andExpect(jsonPath("$.currentGame.teamBRightCourt.playerName").value("P4"))
                .andExpect(jsonPath("$.currentGame.teamBLeftCourt.playerName").value("P3"));
    }

    @Test
    void shouldRejectServerOrReceiverFromTheWrongTeam() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);

        startGame("A", player(3), player(4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The server must be a player on team A."));

        startGame("A", player(1), player(2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The receiver must be a player on team B."));

        // Missing fields are caught by @Valid.
        mockMvc.perform(organizer.authorize(post(matchUrl + "/games"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.servingTeam").value("Choose the team that serves first."));
    }

    @Test
    void shouldUpdateScoreAndServeAfterEachRally() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());

        // A wins: 1-0, P1 serves again from the left, to P4.
        points("A", 1)
                .andExpect(jsonPath("$.currentGame.teamAScore").value(1))
                .andExpect(jsonPath("$.currentGame.server.playerName").value("P1"))
                .andExpect(jsonPath("$.currentGame.teamALeftCourt.playerName").value("P1"))
                .andExpect(jsonPath("$.currentGame.receiver.playerName").value("P4"));

        // B wins: 1-1, service to B. B's score is odd, so P4 (left) serves, to P1 (left).
        points("B", 1)
                .andExpect(jsonPath("$.currentGame.teamBScore").value(1))
                .andExpect(jsonPath("$.currentGame.servingTeam").value("B"))
                .andExpect(jsonPath("$.currentGame.server.playerName").value("P4"))
                .andExpect(jsonPath("$.currentGame.receiver.playerName").value("P1"))
                .andExpect(jsonPath("$.currentGame.ralliesPlayed").value(2));
    }

    @Test
    void shouldStoreWhoServedAndReceivedEveryRally() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());
        points("A", 1);
        points("B", 1);
        entityManager.flush();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.sequence_number, p.scoring_team, p.server_id, p.receiver_id, p.team_a_score, p.team_b_score
                FROM game_points p JOIN match_games g ON g.id = p.game_id
                JOIN matches m ON m.id = g.match_id
                WHERE m.event_id = ?
                ORDER BY p.sequence_number
                """, eventId);

        assertEquals(2, rows.size());
        // Rally 1: P1 served to P3, A won, 1-0.
        assertEquals(player(1), ((Number) rows.get(0).get("server_id")).longValue());
        assertEquals(player(3), ((Number) rows.get(0).get("receiver_id")).longValue());
        assertEquals(1, ((Number) rows.get(0).get("team_a_score")).intValue());
        // Rally 2: P1 served (from the left) to P4, B won, 1-1.
        assertEquals(player(1), ((Number) rows.get(1).get("server_id")).longValue());
        assertEquals(player(4), ((Number) rows.get(1).get("receiver_id")).longValue());
        assertEquals("B", rows.get(1).get("scoring_team").toString().trim());
        assertEquals(1, ((Number) rows.get(1).get("team_b_score")).intValue());
    }

    @Test
    void shouldFinishSingleGameMatchOnlyWhenOrganizerConfirms() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());

        // 21-0: the game is over, but the match waits for the organizer.
        points("A", 21)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.games[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.games[0].winningTeam").value("A"))
                .andExpect(jsonPath("$.matchWinner").value("A"))
                .andExpect(jsonPath("$.nextStep").value("COMPLETE_MATCH"));

        // No more rallies once the game is over.
        mockMvc.perform(organizer.authorize(post(matchUrl + "/points"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"team\": \"B\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("There is no game in progress. Start a game first."));

        // FR-032 / FR-033: confirm the result.
        mockMvc.perform(organizer.authorize(post(matchUrl + "/complete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.nextStep").value("NONE"))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()))
                .andExpect(jsonPath("$.durationSeconds").value(notNullValue()));

        // The match list shows the winner, and the 4 players now have 1 completed match.
        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId + "/matches")))
                .andExpect(jsonPath("$[0].winningTeam").value("A"));
        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId + "/queue")))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].completedMatches").value(1));
    }

    @Test
    void shouldPlayBestOfThreeWithChangesOfEnds() throws Exception {
        setUpMatch("BEST_OF_THREE", null, true);

        // Game 1: A wins 21-0.
        startGame("A", player(1), player(3))
                .andExpect(jsonPath("$.currentGame.endsChanged").value(false));
        points("A", 21)
                .andExpect(jsonPath("$.matchWinner").value(nullValue()))
                .andExpect(jsonPath("$.nextStep").value("START_GAME"));

        // Game 2: the winner of game 1 must serve first.
        startGame("B", player(3), player(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Team A won game 1, so team A serves first in game 2."));
        startGame("A", player(2), player(4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentGame.gameNumber").value(2))
                .andExpect(jsonPath("$.currentGame.endsChanged").value(true));
        points("B", 21).andExpect(jsonPath("$.nextStep").value("START_GAME"));

        // Game 3: B won game 2, so B serves. Ends change when the leader reaches 11.
        startGame("B", player(3), player(1))
                .andExpect(jsonPath("$.currentGame.endsChanged").value(false));
        points("A", 10).andExpect(jsonPath("$.currentGame.changeEndsNow").value(false));
        points("A", 1)
                .andExpect(jsonPath("$.currentGame.changeEndsNow").value(true))
                .andExpect(jsonPath("$.currentGame.endsChanged").value(true));
        points("A", 10)
                .andExpect(jsonPath("$.games", hasSize(3)))
                .andExpect(jsonPath("$.matchWinner").value("A"))
                .andExpect(jsonPath("$.nextStep").value("COMPLETE_MATCH"));

        // A 4th game isn't possible.
        startGame("A", player(1), player(3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This match already has a winner. Complete the match instead."));
    }

    @Test
    void shouldChangeEndsAtOrganizersScoreInSingleGame() throws Exception {
        // FR-025: change ends when a team reaches 11.
        setUpMatch("SINGLE_GAME", 11, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());

        points("B", 10).andExpect(jsonPath("$.currentGame.endsChanged").value(false));
        points("B", 1)
                .andExpect(jsonPath("$.currentGame.changeEndsNow").value(true))
                .andExpect(jsonPath("$.currentGame.endsChanged").value(true));
    }

    @Test
    void shouldUndoTheWinningRallyAndReopenTheGame() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());
        points("A", 21);

        undo()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentGame.teamAScore").value(20))
                .andExpect(jsonPath("$.currentGame.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentGame.ralliesPlayed").value(20))
                .andExpect(jsonPath("$.matchWinner").value(nullValue()))
                .andExpect(jsonPath("$.nextStep").value("RECORD_POINT"));

        // Play continues normally after the undo.
        points("B", 1).andExpect(jsonPath("$.currentGame.teamBScore").value(1));
    }

    @Test
    void shouldUndoStartGameWhenNoRallyWasPlayed() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        // Oops: wrong server picked.
        startGame("A", player(1), player(3)).andExpect(status().isOk());

        undo()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games", hasSize(0)))
                .andExpect(jsonPath("$.currentGame").value(nullValue()))
                .andExpect(jsonPath("$.nextStep").value("START_GAME"));

        // Nothing left to undo.
        undo()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("There is nothing to undo."));
    }

    @Test
    void shouldNotChangeAMatchAfterItIsCompleted() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());

        // Can't complete without a winner.
        mockMvc.perform(organizer.authorize(post(matchUrl + "/complete")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The match doesn't have a winner yet."));

        points("A", 21);
        mockMvc.perform(organizer.authorize(post(matchUrl + "/complete"))).andExpect(status().isOk());

        undo()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A completed or cancelled match can't be scored."));

        // The scoreboard is still readable for review (FR-034).
        mockMvc.perform(organizer.authorize(get(matchUrl + "/scoreboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[0].teamAScore").value(21))
                .andExpect(jsonPath("$.matchWinner").value("A"));
    }

    @Test
    void shouldRejectInvalidTeamInPoint() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        startGame("A", player(1), player(3)).andExpect(status().isOk());

        mockMvc.perform(organizer.authorize(post(matchUrl + "/points"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"team\": \"C\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotLetAnotherOrganizerScore() throws Exception {
        setUpMatch("SINGLE_GAME", null, true);
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(intruder.authorize(get(matchUrl + "/scoreboard")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(post(matchUrl + "/points"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"team\": \"A\" }"))
                .andExpect(status().isNotFound());
    }
}