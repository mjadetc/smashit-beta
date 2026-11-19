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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for /api/events/{eventId}/expenses. Players: P1, P2, P3.
// (JSON numbers are read back as doubles, so amounts are compared like 1000.0.)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Filled in by setUpEvent().
    private LoggedInUser organizer;
    private Long eventId;
    private List<Long> players;

    // ---------- helpers ----------

    private void setUpEvent() throws Exception {
        organizer = AuthTestHelper.registerAndLogin(mockMvc);
        eventId = createEvent(organizer);

        players = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String body = mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/participants"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"playerName\": \"P" + i + "\" }"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            players.add(idOf(body));
        }
    }

    private Long createEvent(LoggedInUser user) throws Exception {
        String body = mockMvc.perform(user.authorize(post("/api/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Friday Smash",
                                  "location": "Court Hub",
                                  "startsAt": "2026-11-20T19:00:00+08:00",
                                  "endsAt": "2026-11-20T22:00:00+08:00",
                                  "courtCount": 1,
                                  "matchFormat": "SINGLE_GAME"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private String expensesUrl() {
        return "/api/events/" + eventId + "/expenses";
    }

    private Long player(int number) {
        return players.get(number - 1);
    }

    private Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // The JSON body for an expense. `amount` is written as-is, e.g. "1500.00".
    private String expenseJson(String description, String category, String amount, Long paidById) {
        return """
                {
                  "description": "%s",
                  "category": "%s",
                  "amount": %s,
                  "expenseDate": "2026-11-20",
                  "paidById": %d
                }
                """.formatted(description, category, amount, paidById);
    }

    private ResultActions postExpense(String json) throws Exception {
        return mockMvc.perform(organizer.authorize(post(expensesUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // Records an expense, expecting 201, and returns its id.
    private Long addExpense(String category, String amount, Long paidById) throws Exception {
        String body = postExpense(expenseJson("Something", category, amount, paidById))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    // ---------- tests ----------

    @Test
    void shouldRecordAndListExpenses() throws Exception {
        setUpEvent();

        postExpense(expenseJson("  Court 1, 3 hours  ", "COURT_RENTAL", "1500.00", player(1)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.description").value("Court 1, 3 hours"))
                .andExpect(jsonPath("$.category").value("COURT_RENTAL"))
                .andExpect(jsonPath("$.amount").value(1500.0))
                .andExpect(jsonPath("$.expenseDate").value("2026-11-20"))
                .andExpect(jsonPath("$.paidBy.playerName").value("P1"));

        addExpense("SHUTTLECOCKS", "350.50", player(2));

        mockMvc.perform(organizer.authorize(get(expensesUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].amount").value(350.5));
    }

    @Test
    void shouldValidateTheExpense() throws Exception {
        setUpEvent();

        mockMvc.perform(organizer.authorize(post(expensesUrl()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").value("Description is required."))
                .andExpect(jsonPath("$.errors.category").value("Category is required."))
                .andExpect(jsonPath("$.errors.amount").value("Amount is required."))
                .andExpect(jsonPath("$.errors.expenseDate").value("Date is required."))
                .andExpect(jsonPath("$.errors.paidById").value("Choose who paid."));

        postExpense(expenseJson("Balls", "SHUTTLECOCKS", "0", player(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount must be at least 0.01."));

        postExpense(expenseJson("Balls", "SHUTTLECOCKS", "10.555", player(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount")
                        .value("Amount can have at most 8 digits and 2 decimal places."));

        // Unknown category -> the JSON can't be read.
        postExpense(expenseJson("Balls", "SNACKS", "10.00", player(1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPayerWhoIsNotAnActiveParticipant() throws Exception {
        setUpEvent();

        // Someone from another event.
        Long otherEvent = createEvent(organizer);
        String otherBody = mockMvc.perform(organizer.authorize(post("/api/events/" + otherEvent + "/participants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"Stranger\" }"))
                .andReturn().getResponse().getContentAsString();
        postExpense(expenseJson("Court", "COURT_RENTAL", "100.00", idOf(otherBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The payer must be an active participant of this event."));

        // Someone who was removed from this event.
        mockMvc.perform(organizer.authorize(delete("/api/events/" + eventId + "/participants/" + player(3))))
                .andExpect(status().isNoContent());
        postExpense(expenseJson("Court", "COURT_RENTAL", "100.00", player(3)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldEditAndDeleteAnExpense() throws Exception {
        setUpEvent();
        Long expenseId = addExpense("COURT_RENTAL", "1000.00", player(1));
        String url = expensesUrl() + "/" + expenseId;

        mockMvc.perform(organizer.authorize(put(url))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Court, corrected", "COURT_RENTAL", "1200.00", player(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1200.0))
                .andExpect(jsonPath("$.paidBy.playerName").value("P2"));

        mockMvc.perform(organizer.authorize(delete(url)))
                .andExpect(status().isNoContent());
        mockMvc.perform(organizer.authorize(delete(url)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldStillEditExpenseWhosePayerHasLeft() throws Exception {
        setUpEvent();
        Long expenseId = addExpense("FOOD_AND_DRINKS", "300.00", player(3));
        mockMvc.perform(organizer.authorize(delete("/api/events/" + eventId + "/participants/" + player(3))))
                .andExpect(status().isNoContent());

        // Same (removed) payer: fixing the amount is fine.
        mockMvc.perform(organizer.authorize(put(expensesUrl() + "/" + expenseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Water", "FOOD_AND_DRINKS", "330.00", player(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(330.0));
    }

    @Test
    void shouldSummarizeSharesBalancesAndSettlements() throws Exception {
        setUpEvent();
        // 1200.00 in total for 3 people = 400.00 each.
        addExpense("COURT_RENTAL", "1000.00", player(1));
        addExpense("SHUTTLECOCKS", "200.00", player(2));

        mockMvc.perform(organizer.authorize(get(expensesUrl() + "/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(1200.0))
                .andExpect(jsonPath("$.sharingParticipants").value(3))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].category").value("COURT_RENTAL"))
                .andExpect(jsonPath("$.categories[0].amount").value(1000.0))
                // P1 paid 1000, owes 400 -> is owed 600.
                .andExpect(jsonPath("$.balances[0].playerName").value("P1"))
                .andExpect(jsonPath("$.balances[0].paid").value(1000.0))
                .andExpect(jsonPath("$.balances[0].share").value(400.0))
                .andExpect(jsonPath("$.balances[0].balance").value(600.0))
                // P2 paid 200 -> owes 200. P3 paid nothing -> owes 400.
                .andExpect(jsonPath("$.balances[1].balance").value(-200.0))
                .andExpect(jsonPath("$.balances[2].balance").value(-400.0))
                // Biggest debt first: P3 pays P1 400, then P2 pays P1 200.
                .andExpect(jsonPath("$.settlements", hasSize(2)))
                .andExpect(jsonPath("$.settlements[0].from.playerName").value("P3"))
                .andExpect(jsonPath("$.settlements[0].to.playerName").value("P1"))
                .andExpect(jsonPath("$.settlements[0].amount").value(400.0))
                .andExpect(jsonPath("$.settlements[1].from.playerName").value("P2"))
                .andExpect(jsonPath("$.settlements[1].amount").value(200.0));
    }

    @Test
    void shouldStillRepayARemovedPlayerWhoPaid() throws Exception {
        setUpEvent();
        // P3 paid 300.00 for the court, then left. P1 and P2 share it: 150.00 each.
        addExpense("COURT_RENTAL", "300.00", player(3));
        mockMvc.perform(organizer.authorize(delete("/api/events/" + eventId + "/participants/" + player(3))))
                .andExpect(status().isNoContent());

        mockMvc.perform(organizer.authorize(get(expensesUrl() + "/summary")))
                .andExpect(jsonPath("$.sharingParticipants").value(2))
                .andExpect(jsonPath("$.balances", hasSize(3)))
                .andExpect(jsonPath("$.balances[2].playerName").value("P3"))
                .andExpect(jsonPath("$.balances[2].active").value(false))
                .andExpect(jsonPath("$.balances[2].share").value(0.0))
                .andExpect(jsonPath("$.balances[2].balance").value(300.0))
                .andExpect(jsonPath("$.settlements[0].to.playerName").value("P3"))
                .andExpect(jsonPath("$.settlements[0].amount").value(150.0));
    }

    @Test
    void shouldAllowExpensesAfterTheEventIsCompleted() throws Exception {
        setUpEvent();
        mockMvc.perform(organizer.authorize(post("/api/events/" + eventId + "/complete")))
                .andExpect(status().isOk());

        // Settling up after the session is normal.
        postExpense(expenseJson("Court", "COURT_RENTAL", "900.00", player(1)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldNotLetAnotherOrganizerSeeOrAddExpenses() throws Exception {
        setUpEvent();
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(intruder.authorize(get(expensesUrl())))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(get(expensesUrl() + "/summary")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(post(expensesUrl()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Court", "COURT_RENTAL", "100.00", player(1))))
                .andExpect(status().isNotFound());
    }
}