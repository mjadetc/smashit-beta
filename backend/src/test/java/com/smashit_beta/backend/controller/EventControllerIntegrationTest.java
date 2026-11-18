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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for /api/events: real security, controller, service and PostgreSQL.
// @Transactional rolls back each test's data.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // A valid event body; %s placeholders let tests change the name and match format.
    private String eventJson(String name, String matchFormat, String changeEndsAt) {
        return """
                {
                  "name": "%s",
                  "location": "Court Hub, Quezon City",
                  "startsAt": "2026-11-20T19:00:00+08:00",
                  "endsAt": "2026-11-20T22:00:00+08:00",
                  "courtCount": 2,
                  "matchFormat": "%s",
                  "changeEndsAt": %s,
                  "description": "Bring your own racket."
                }
                """.formatted(name, matchFormat, changeEndsAt);
    }

    // Creates an event as the given user and returns its id.
    private Long createEvent(LoggedInUser user, String name) throws Exception {
        String body = mockMvc.perform(user.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(name, "SINGLE_GAME", "11")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // JsonPath.read pulls one value out of a JSON string.
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void shouldCreateEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(organizer.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Friday Smash", "SINGLE_GAME", "11")))
                .andExpect(status().isCreated())
                // The Location header points at the new event, e.g. .../api/events/12
                .andExpect(header().string("Location", matchesPattern(".*/api/events/\\d+$")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.organizerId").isNumber())
                .andExpect(jsonPath("$.name").value("Friday Smash"))
                .andExpect(jsonPath("$.matchFormat").value("SINGLE_GAME"))
                .andExpect(jsonPath("$.changeEndsAt").value(11))
                .andExpect(jsonPath("$.courtCount").value(2));
    }

    @Test
    void shouldRequireLoginToListEvents() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnFieldErrorsForInvalidEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(organizer.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "", "location": "Court Hub", "courtCount": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Event name is required."))
                .andExpect(jsonPath("$.errors.courtCount").value("There must be at least 1 court."))
                .andExpect(jsonPath("$.errors.startsAt").value("Start time is required."))
                .andExpect(jsonPath("$.errors.matchFormat").value("Match format is required."));
    }

    @Test
    void shouldRejectUnknownMatchFormat() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(organizer.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Friday Smash", "BEST_OF_FIVE", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("The request body is malformed or contains an invalid value."));
    }

    @Test
    void shouldRejectChangeEndsAtForBestOfThree() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(organizer.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Friday Smash", "BEST_OF_THREE", "11")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Change of ends can only be set for the single-game format."));
    }

    @Test
    void shouldListOnlyMyEvents() throws Exception {
        LoggedInUser alice = AuthTestHelper.registerAndLogin(mockMvc);
        LoggedInUser bob = AuthTestHelper.registerAndLogin(mockMvc);

        createEvent(alice, "Alice Event 1");
        createEvent(alice, "Alice Event 2");
        createEvent(bob, "Bob Event");

        // Alice sees her 2 events, not Bob's.
        mockMvc.perform(alice.authorize(get("/api/events")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldGetAndUpdateMyEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, "Friday Smash");

        mockMvc.perform(organizer.authorize(put("/api/events/" + eventId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Saturday Smash", "BEST_OF_THREE", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saturday Smash"))
                .andExpect(jsonPath("$.matchFormat").value("BEST_OF_THREE"));

        // Reading it again proves the update was saved.
        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saturday Smash"));
    }

    @Test
    void shouldNotLetAnotherOrganizerSeeEditOrDeleteMyEvent() throws Exception {
        LoggedInUser owner = AuthTestHelper.registerAndLogin(mockMvc);
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(owner, "Private Event");

        // 404 (not 403), so the intruder can't even tell the event exists.
        mockMvc.perform(intruder.authorize(get("/api/events/" + eventId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(intruder.authorize(put("/api/events/" + eventId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Hacked", "SINGLE_GAME", "null")))
                .andExpect(status().isNotFound());

        mockMvc.perform(intruder.authorize(delete("/api/events/" + eventId)))
                .andExpect(status().isNotFound());

        // The owner's event is untouched.
        mockMvc.perform(owner.authorize(get("/api/events/" + eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Private Event"));
    }

    @Test
    void shouldDeleteMyEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, "Friday Smash");

        mockMvc.perform(organizer.authorize(delete("/api/events/" + eventId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateEventAsActive() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, "Friday Smash");

        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldHideCompletedAndCancelledEventsUnlessFiltered() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        createEvent(organizer, "Upcoming Event");
        Long doneId = createEvent(organizer, "Finished Event");
        Long cancelledId = createEvent(organizer, "Rained Out Event");

        mockMvc.perform(organizer.authorize(post("/api/events/" + doneId + "/complete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(organizer.authorize(post("/api/events/" + cancelledId + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Default list: only the ACTIVE event.
        mockMvc.perform(organizer.authorize(get("/api/events")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Upcoming Event"));

        // Filtered lists: each hidden event is still reachable.
        mockMvc.perform(organizer.authorize(get("/api/events").param("status", "COMPLETED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Finished Event"));

        mockMvc.perform(organizer.authorize(get("/api/events").param("status", "CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Rained Out Event"));

        // Opening a hidden event directly still works.
        mockMvc.perform(organizer.authorize(get("/api/events/" + doneId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldRejectUnknownStatusFilter() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(organizer.authorize(get("/api/events").param("status", "FINISHED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for 'status'."));
    }

    @Test
    void shouldNotEditCompletedEventUntilReactivated() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, "Friday Smash");

        mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/complete")))
                .andExpect(status().isOk());

        // Editing a completed event -> 409 Conflict.
        mockMvc.perform(organizer.authorize(put("/api/events/" + eventId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Saturday Smash", "SINGLE_GAME", "null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Only active events can be edited. Reactivate it first."));

        // A completed event can't be cancelled either -> 409.
        mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A completed event can't be cancelled."));

        // Reactivate, then editing works again.
        mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/reactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(organizer.authorize(put("/api/events/" + eventId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Saturday Smash", "SINGLE_GAME", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saturday Smash"));
    }

    @Test
    void shouldNotLetAnotherOrganizerChangeMyEventStatus() throws Exception {
        LoggedInUser owner = AuthTestHelper.registerAndLogin(mockMvc);
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(owner, "Private Event");

        mockMvc.perform(intruder.authorize(post("/api/events/" + eventId + "/complete")))
                .andExpect(status().isNotFound());

        mockMvc.perform(intruder.authorize(post("/api/events/" + eventId + "/cancel")))
                .andExpect(status().isNotFound());

        mockMvc.perform(owner.authorize(get("/api/events/" + eventId)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldDeleteCompletedEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer, "Friday Smash");

        mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/complete")))
                .andExpect(status().isOk());

        // "Remove" works on any status, and the event is gone for good.
        mockMvc.perform(organizer.authorize(delete("/api/events/" + eventId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(organizer.authorize(get("/api/events/" + eventId)))
                .andExpect(status().isNotFound());
    }
}