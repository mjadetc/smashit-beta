package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.support.AuthTestHelper;
import com.smashit_beta.backend.support.AuthTestHelper.LoggedInUser;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for /player-stats and /summary, plus the event changes that
// protect unfinished matches. Matches are played through the real scoring API.
// The event: 1 court, single game, players P1..P5.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Filled in by setUpEvent().
    private LoggedInUser organizer;
    private Long eventId;
    private List<Long> players;

    // ---------- helpers ----------

    private void setUpEvent() throws Exception {
        organizer = AuthTestHelper.registerAndLogin(mockMvc);

        String eventJson = mockMvc.perform(organizer.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("SINGLE_GAME", 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        eventId = idOf(eventJson);

        players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String body = mockMvc.perform(organizer.authorize(post(eventUrl() + "/participants"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"playerName\": \"P" + i + "\" }"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            players.add(idOf(body));
        }
    }

    // The JSON for creating or editing the test event.
    private String eventJson(String matchFormat, int courtCount) {
        return """
                {
                  "name": "Friday Smash",
                  "location": "Court Hub",
                  "startsAt": "2026-11-20T19:00:00+08:00",
                  "endsAt": "2026-11-20T22:00:00+08:00",
                  "courtCount": %d,
                  "matchFormat": "%s"
                }
                """.formatted(courtCount, matchFormat);
    }

    private String eventUrl() {
        return "/api/events/" + eventId;
    }

    private Long player(int number) {
        return players.get(number - 1);
    }

    private Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // Creates a match on court 1 with these exact players and returns its URL.
    private String createMatch(int a1, int a2, int b1, int b2) throws Exception {
        String json = mockMvc.perform(organizer.authorize(post(eventUrl() + "/matches"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1, \"teamA\": [%d, %d], \"teamB\": [%d, %d] }"
                                .formatted(player(a1), player(a2), player(b1), player(b2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return eventUrl() + "/matches/" + idOf(json);
    }

    private void post200(String url) throws Exception {
        mockMvc.perform(organizer.authorize(post(url))).andExpect(status().isOk());
    }

    // Starts the match and game 1 (team A's first player serving), then records
    // the rallies: pointsB for team B first, then pointsA for team A.
    private void play(String matchUrl, int pointsA, int pointsB) throws Exception {
        post200(matchUrl + "/start");
        Long server = ((Number) JsonPath.read(getJson(matchUrl + "/scoreboard"), "$.teamA[0].participantId")).longValue();
        Long receiver = ((Number) JsonPath.read(getJson(matchUrl + "/scoreboard"), "$.teamB[0].participantId")).longValue();
        mockMvc.perform(organizer.authorize(post(matchUrl + "/games"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"servingTeam\": \"A\", \"serverId\": %d, \"receiverId\": %d }"
                                .formatted(server, receiver)))
                .andExpect(status().isOk());
        rally(matchUrl, "B", pointsB);
        rally(matchUrl, "A", pointsA);
    }

    private void rally(String matchUrl, String team, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mockMvc.perform(organizer.authorize(post(matchUrl + "/points"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"team\": \"" + team + "\" }"))
                    .andExpect(status().isOk());
        }
    }

    private String getJson(String url) throws Exception {
        return mockMvc.perform(organizer.authorize(get(url)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------- player stats (FR-036, FR-037) ----------

    @Test
    void shouldCountOnlyCompletedMatchesInPlayerStats() throws Exception {
        setUpEvent();

        // Match 1: P1 + P2 beat P3 + P4, 21-5. Completed.
        String first = createMatch(1, 2, 3, 4);
        play(first, 21, 5);
        post200(first + "/complete");

        // Match 2: started and scored, then cancelled. Must not count (FR-035).
        String second = createMatch(1, 3, 2, 4);
        play(second, 3, 0);
        post200(second + "/cancel");

        // Order: most wins, then fewest losses, then A to Z:
        // P1, P2 (1 win), P5 (no matches, 0 losses), P3, P4 (1 loss).
        mockMvc.perform(organizer.authorize(get(eventUrl() + "/player-stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].playerName").value("P1"))
                .andExpect(jsonPath("$[0].matchesPlayed").value(1))
                .andExpect(jsonPath("$[0].wins").value(1))
                .andExpect(jsonPath("$[0].gamesWon").value(1))
                .andExpect(jsonPath("$[0].pointsWon").value(21))
                .andExpect(jsonPath("$[0].pointsLost").value(5))
                .andExpect(jsonPath("$[1].playerName").value("P2"))
                .andExpect(jsonPath("$[2].playerName").value("P5"))
                .andExpect(jsonPath("$[2].matchesPlayed").value(0))
                .andExpect(jsonPath("$[3].playerName").value("P3"))
                .andExpect(jsonPath("$[3].losses").value(1))
                .andExpect(jsonPath("$[3].gamesLost").value(1))
                .andExpect(jsonPath("$[3].pointsWon").value(5));
    }

    @Test
    void shouldUpdateStatsWhenMatchIsCompleted() throws Exception {
        setUpEvent();
        String match = createMatch(1, 2, 3, 4);
        play(match, 21, 0);

        // The game is won, but the organizer hasn't confirmed the match yet: nothing counts.
        mockMvc.perform(organizer.authorize(get(eventUrl() + "/player-stats")))
                .andExpect(jsonPath("$[0].matchesPlayed").value(0));

        // FR-037: once completed, the next request includes it.
        post200(match + "/complete");
        mockMvc.perform(organizer.authorize(get(eventUrl() + "/player-stats")))
                .andExpect(jsonPath("$[0].playerName").value("P1"))
                .andExpect(jsonPath("$[0].matchesPlayed").value(1));
    }

    @Test
    void shouldKeepRemovedPlayerWhoFinishedAMatch() throws Exception {
        setUpEvent();
        String match = createMatch(1, 2, 3, 4);
        play(match, 21, 0);
        post200(match + "/complete");

        // P1 played and leaves early; P5 never played and leaves too.
        mockMvc.perform(organizer.authorize(delete(eventUrl() + "/participants/" + player(1))))
                .andExpect(status().isNoContent());
        mockMvc.perform(organizer.authorize(delete(eventUrl() + "/participants/" + player(5))))
                .andExpect(status().isNoContent());

        // P1 stays in the standings (marked inactive); P5 disappears.
        mockMvc.perform(organizer.authorize(get(eventUrl() + "/player-stats")))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].playerName").value("P1"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].wins").value(1));
    }

    // ---------- summary (FR-038) ----------

    @Test
    void shouldSummarizeTheEvent() throws Exception {
        setUpEvent();
        String first = createMatch(1, 2, 3, 4);
        play(first, 21, 5);
        post200(first + "/complete");

        String second = createMatch(1, 3, 2, 4);
        post200(second + "/cancel");

        // A third match is waiting on court 1.
        String third = createMatch(5, 1, 2, 3);
        Long thirdId = Long.valueOf(third.substring(third.lastIndexOf('/') + 1));

        mockMvc.perform(organizer.authorize(get(eventUrl() + "/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventName").value("Friday Smash"))
                .andExpect(jsonPath("$.activeParticipants").value(5))
                .andExpect(jsonPath("$.scheduledMatches").value(1))
                .andExpect(jsonPath("$.inProgressMatches").value(0))
                .andExpect(jsonPath("$.completedMatches").value(1))
                .andExpect(jsonPath("$.cancelledMatches").value(1))
                .andExpect(jsonPath("$.gamesPlayed").value(1))
                .andExpect(jsonPath("$.ralliesPlayed").value(26))
                .andExpect(jsonPath("$.averageMatchDurationSeconds").value(notNullValue()))
                .andExpect(jsonPath("$.longestMatchDurationSeconds").value(notNullValue()))
                .andExpect(jsonPath("$.courts", hasSize(1)))
                .andExpect(jsonPath("$.courts[0].completedMatches").value(1))
                .andExpect(jsonPath("$.courts[0].currentMatchId").value(thirdId))
                .andExpect(jsonPath("$.courts[0].currentMatchStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].teamA[0].playerName").value("P1"))
                .andExpect(jsonPath("$.results[0].winningTeam").value("A"))
                .andExpect(jsonPath("$.results[0].games[0].teamAScore").value(21))
                .andExpect(jsonPath("$.results[0].games[0].teamBScore").value(5));
    }

    @Test
    void shouldSummarizeEventWithNoMatches() throws Exception {
        setUpEvent();

        mockMvc.perform(organizer.authorize(get(eventUrl() + "/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedMatches").value(0))
                .andExpect(jsonPath("$.averageMatchDurationSeconds").value(nullValue()))
                .andExpect(jsonPath("$.courts[0].currentMatchId").value(nullValue()))
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    // ---------- events protect unfinished matches ----------

    @Test
    void shouldCancelUnfinishedMatchesWhenEventIsCompleted() throws Exception {
        setUpEvent();
        String match = createMatch(1, 2, 3, 4);
        post200(match + "/start");

        post200(eventUrl() + "/complete");

        mockMvc.perform(organizer.authorize(get(match)))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(organizer.authorize(get(eventUrl() + "/summary")))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cancelledMatches").value(1))
                .andExpect(jsonPath("$.inProgressMatches").value(0));
    }

    @Test
    void shouldNotChangeFormatWhileMatchIsBeingPlayed() throws Exception {
        setUpEvent();
        String match = createMatch(1, 2, 3, 4);
        post200(match + "/start");

        mockMvc.perform(organizer.authorize(put(eventUrl()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("BEST_OF_THREE", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The match format and change of ends can't change while a match is being played."));

        // Once the match is cancelled, the format can change.
        post200(match + "/cancel");
        mockMvc.perform(organizer.authorize(put(eventUrl()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("BEST_OF_THREE", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchFormat").value("BEST_OF_THREE"));
    }

    @Test
    void shouldNotLetAnotherOrganizerSeeStats() throws Exception {
        setUpEvent();
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(intruder.authorize(get(eventUrl() + "/player-stats")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(get(eventUrl() + "/summary")))
                .andExpect(status().isNotFound());
    }
}