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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for /api/events/{eventId}/participants.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParticipantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------- helpers ----------

    // Creates an event for this user and returns its id.
    private Long createEvent(LoggedInUser user) throws Exception {
        String body = mockMvc.perform(user.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Friday Smash",
                                  "location": "Court Hub",
                                  "startsAt": "2026-11-20T19:00:00+08:00",
                                  "endsAt": "2026-11-20T22:00:00+08:00",
                                  "courtCount": 2,
                                  "matchFormat": "SINGLE_GAME"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // URL of an event's participant list, e.g. /api/events/5/participants
    private String participantsUrl(Long eventId) {
        return "/api/events/" + eventId + "/participants";
    }

    // Adds a participant and returns their id.
    private Long addParticipant(LoggedInUser user, Long eventId, String name) throws Exception {
        String body = mockMvc.perform(user.authorize(post(participantsUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"" + name + "\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ---------- tests ----------

    @Test
    void shouldAddAndListParticipantsAlphabetically() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer);

        addParticipant(organizer, eventId, "Maria");
        addParticipant(organizer, eventId, "Ana");
        addParticipant(organizer, eventId, "Leo");

        mockMvc.perform(organizer.authorize(get(participantsUrl(eventId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].playerName").value("Ana"))
                .andExpect(jsonPath("$[1].playerName").value("Leo"))
                .andExpect(jsonPath("$[2].playerName").value("Maria"))
                .andExpect(jsonPath("$[0].eventId").value(eventId))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void shouldRejectDuplicateNameIgnoringCase() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer);
        addParticipant(organizer, eventId, "Juan");

        mockMvc.perform(organizer.authorize(post(participantsUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"  JUAN \" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A participant named \"Juan\" is already in this event."));
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer);

        mockMvc.perform(organizer.authorize(post(participantsUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"   \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.playerName").value("Player name is required."));
    }

    @Test
    void shouldRenameParticipant() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer);
        Long juanId = addParticipant(organizer, eventId, "Juan");
        addParticipant(organizer, eventId, "Maria");

        mockMvc.perform(organizer.authorize(put(participantsUrl(eventId) + "/" + juanId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"Juan D.\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Juan D."));

        // Renaming to someone else's name -> 409.
        mockMvc.perform(organizer.authorize(put(participantsUrl(eventId) + "/" + juanId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"maria\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRemoveParticipantAndRestoreWhenAddedAgain() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer);
        Long juanId = addParticipant(organizer, eventId, "Juan");

        mockMvc.perform(organizer.authorize(delete(participantsUrl(eventId) + "/" + juanId)))
                .andExpect(status().isNoContent());

        // Removed participants disappear from the list.
        mockMvc.perform(organizer.authorize(get(participantsUrl(eventId))))
                .andExpect(jsonPath("$", hasSize(0)));

        // Adding the same name again brings back the SAME participant (same id).
        mockMvc.perform(organizer.authorize(post(participantsUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"Juan\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(juanId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldNotChangeParticipantsOfCancelledEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(organizer);
        addParticipant(organizer, eventId, "Juan");

        mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/cancel")))
                .andExpect(status().isOk());

        mockMvc.perform(organizer.authorize(post(participantsUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"Maria\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Participants can only be changed while the event is active."));

        // Viewing is still allowed.
        mockMvc.perform(organizer.authorize(get(participantsUrl(eventId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shouldNotLetAnotherOrganizerSeeOrChangeParticipants() throws Exception {
        LoggedInUser owner = AuthTestHelper.registerAndLogin(mockMvc);
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);
        Long eventId = createEvent(owner);
        Long juanId = addParticipant(owner, eventId, "Juan");

        mockMvc.perform(intruder.authorize(get(participantsUrl(eventId))))
                .andExpect(status().isNotFound());

        mockMvc.perform(intruder.authorize(post(participantsUrl(eventId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"Hacker\" }"))
                .andExpect(status().isNotFound());

        mockMvc.perform(intruder.authorize(delete(participantsUrl(eventId) + "/" + juanId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotUseParticipantIdFromAnotherEvent() throws Exception {
        LoggedInUser organizer = AuthTestHelper.registerAndLogin(mockMvc);
        Long fridayId = createEvent(organizer);
        Long saturdayId = createEvent(organizer);
        Long juanId = addParticipant(organizer, fridayId, "Juan");

        // Juan belongs to Friday's event, so Saturday's URL can't touch him.
        mockMvc.perform(organizer.authorize(delete(participantsUrl(saturdayId) + "/" + juanId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Participant not found."));
    }
}