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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION tests for /api/events/{eventId}/expenses and /contributions.
// Players: P1, P2, P3 (P4 too, in the tests that need a match).
// (JSON numbers are read back as doubles, so amounts are compared like 1000.0.)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // To fake a completed match (like MatchControllerIntegrationTest does).
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    // Filled in by setUpEvent().
    private LoggedInUser organizer;
    private Long eventId;
    private List<Long> players;

    // ---------- helpers ----------

    // An event with `count` players, named P1, P2, ...
    private void setUpEvent(int count) throws Exception {
        organizer = AuthTestHelper.registerAndLogin(mockMvc);
        eventId = createEvent(organizer);

        players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            players.add(addParticipant(eventId, "P" + i));
        }
    }

    private void setUpEvent() throws Exception {
        setUpEvent(3);
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

    private Long addParticipant(Long event, String name) throws Exception {
        String body = mockMvc.perform(organizer.authorize(post("/api/events/" + event + "/participants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"playerName\": \"" + name + "\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private void removeParticipant(Long participantId) throws Exception {
        mockMvc.perform(organizer.authorize(delete("/api/events/" + eventId + "/participants/" + participantId)))
                .andExpect(status().isNoContent());
    }

    private String eventUrl() {
        return "/api/events/" + eventId;
    }

    private String expensesUrl() {
        return eventUrl() + "/expenses";
    }

    private Long player(int number) {
        return players.get(number - 1);
    }

    private Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // The JSON body for an expense paid by a participant. `amount` is written as-is.
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

    // The JSON body for an expense paid by someone outside the event.
    private String outsiderExpenseJson(String amount, String name, boolean repaid) {
        return """
                {
                  "description": "Court",
                  "category": "COURT_RENTAL",
                  "amount": %s,
                  "expenseDate": "2026-11-20",
                  "outsidePayerName": "%s",
                  "outsidePayerRepaid": %s
                }
                """.formatted(amount, name, repaid);
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

    private ResultActions postContribution(String json) throws Exception {
        return mockMvc.perform(organizer.authorize(post(eventUrl() + "/contributions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // "Shares equally?" toggle. `value` is "true", "false" or "null".
    private ResultActions setSharing(Long participantId, String value) throws Exception {
        return mockMvc.perform(organizer.authorize(put(expensesUrl() + "/sharing/" + participantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"sharesEqually\": " + value + " }"));
    }

    private ResultActions summary() throws Exception {
        return mockMvc.perform(organizer.authorize(get(expensesUrl() + "/summary")));
    }

    // ---------- expenses ----------

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
                .andExpect(jsonPath("$.paidBy.playerName").value("P1"))
                .andExpect(jsonPath("$.outsidePayerName").value(nullValue()));

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
                .andExpect(jsonPath("$.errors.expenseDate").value("Date is required."));

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

    // CHANGED (V11): the payer can be a participant OR an outsider, but exactly one.
    @Test
    void shouldRequireExactlyOnePayer() throws Exception {
        setUpEvent();
        String message = "Choose who paid: a participant, or the name of someone outside the event (not both).";

        postExpense("""
                { "description": "Court", "category": "COURT_RENTAL", "amount": 100, "expenseDate": "2026-11-20" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(message));

        postExpense("""
                { "description": "Court", "category": "COURT_RENTAL", "amount": 100, "expenseDate": "2026-11-20",
                  "paidById": %d, "outsidePayerName": "Coach Jun" }
                """.formatted(player(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void shouldRejectPayerWhoIsNotAnActiveParticipant() throws Exception {
        setUpEvent();

        // Someone from another event.
        Long otherEvent = createEvent(organizer);
        Long stranger = addParticipant(otherEvent, "Stranger");
        postExpense(expenseJson("Court", "COURT_RENTAL", "100.00", stranger))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The payer must be an active participant of this event."));

        // Someone who was removed from this event.
        removeParticipant(player(3));
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

        // Switch the payer to someone outside the event.
        mockMvc.perform(organizer.authorize(put(url))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outsiderExpenseJson("1200.00", "Coach Jun", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidBy").value(nullValue()))
                .andExpect(jsonPath("$.outsidePayerName").value("Coach Jun"))
                .andExpect(jsonPath("$.outsidePayerRepaid").value(true));

        mockMvc.perform(organizer.authorize(delete(url)))
                .andExpect(status().isNoContent());
        mockMvc.perform(organizer.authorize(delete(url)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldStillEditExpenseWhosePayerHasLeft() throws Exception {
        setUpEvent();
        Long expenseId = addExpense("FOOD_AND_DRINKS", "300.00", player(3));
        removeParticipant(player(3));

        // Same (removed) payer: fixing the amount is fine.
        mockMvc.perform(organizer.authorize(put(expensesUrl() + "/" + expenseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Water", "FOOD_AND_DRINKS", "330.00", player(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(330.0));
    }

    @Test
    void shouldAllowExpensesAfterTheEventIsCompleted() throws Exception {
        setUpEvent();
        mockMvc.perform(organizer.authorize(post(eventUrl() + "/complete")))
                .andExpect(status().isOk());

        // Settling up after the session is normal.
        postExpense(expenseJson("Court", "COURT_RENTAL", "900.00", player(1)))
                .andExpect(status().isCreated());
    }

    // ---------- summary ----------

    @Test
    void shouldSummarizeSharesBalancesAndSettlements() throws Exception {
        setUpEvent();
        // 1200.00 in total for 3 people = 400.00 each.
        addExpense("COURT_RENTAL", "1000.00", player(1));
        addExpense("SHUTTLECOCKS", "200.00", player(2));

        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareRounding").value("NONE"))
                .andExpect(jsonPath("$.totalExpenses").value(1200.0))
                .andExpect(jsonPath("$.toCover").value(1200.0))
                .andExpect(jsonPath("$.sharingParticipants").value(3))
                .andExpect(jsonPath("$.equalShare").value(400.0))
                .andExpect(jsonPath("$.surplus").value(0.0))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].category").value("COURT_RENTAL"))
                // P1 paid 1000, owes 400 -> receives 600.
                .andExpect(jsonPath("$.balances[0].person.playerName").value("P1"))
                .andExpect(jsonPath("$.balances[0].paid").value(1000.0))
                .andExpect(jsonPath("$.balances[0].share").value(400.0))
                .andExpect(jsonPath("$.balances[0].balance").value(600.0))
                // P2 paid 200 -> pays 200. P3 paid nothing -> pays 400.
                .andExpect(jsonPath("$.balances[1].balance").value(-200.0))
                .andExpect(jsonPath("$.balances[2].balance").value(-400.0))
                // Biggest debt first: P3 pays P1 400, then P2 pays P1 200.
                .andExpect(jsonPath("$.settlements", hasSize(2)))
                .andExpect(jsonPath("$.settlements[0].from.playerName").value("P3"))
                .andExpect(jsonPath("$.settlements[0].to.playerName").value("P1"))
                .andExpect(jsonPath("$.settlements[0].amount").value(400.0))
                // NEW: the same, grouped by who receives.
                .andExpect(jsonPath("$.receivers", hasSize(1)))
                .andExpect(jsonPath("$.receivers[0].person.playerName").value("P1"))
                .andExpect(jsonPath("$.receivers[0].total").value(600.0))
                .andExpect(jsonPath("$.receivers[0].payments", hasSize(2)));
    }

    @Test
    void shouldStillRepayARemovedPlayerWhoPaid() throws Exception {
        setUpEvent();
        // P3 paid 300.00 for the court, then left without playing. P1 and P2 share it.
        addExpense("COURT_RENTAL", "300.00", player(3));
        removeParticipant(player(3));

        summary()
                .andExpect(jsonPath("$.sharingParticipants").value(2))
                .andExpect(jsonPath("$.balances", hasSize(3)))
                .andExpect(jsonPath("$.balances[2].person.playerName").value("P3"))
                .andExpect(jsonPath("$.balances[2].active").value(false))
                .andExpect(jsonPath("$.balances[2].sharesEqually").value(false))
                .andExpect(jsonPath("$.balances[2].balance").value(300.0))
                .andExpect(jsonPath("$.settlements[0].to.playerName").value("P3"))
                .andExpect(jsonPath("$.settlements[0].amount").value(150.0));
    }

    // ---------- NEW: outside payers ----------

    @Test
    void shouldPayBackAnOutsidePayer() throws Exception {
        setUpEvent();
        // Coach Jun (not playing) paid 200 + 100; typed slightly differently the second time.
        postExpense(outsiderExpenseJson("200.00", "Coach Jun", true)).andExpect(status().isCreated());
        postExpense(outsiderExpenseJson("100.00", " coach jun", true)).andExpect(status().isCreated());

        summary()
                .andExpect(jsonPath("$.toCover").value(300.0))
                .andExpect(jsonPath("$.equalShare").value(100.0))
                // P1, P2, P3, then the one outsider.
                .andExpect(jsonPath("$.balances", hasSize(4)))
                .andExpect(jsonPath("$.balances[3].person.participantId").value(nullValue()))
                .andExpect(jsonPath("$.balances[3].person.playerName").value("Coach Jun"))
                .andExpect(jsonPath("$.balances[3].outsidePayer").value(true))
                .andExpect(jsonPath("$.balances[3].balance").value(300.0))
                .andExpect(jsonPath("$.receivers[0].person.playerName").value("Coach Jun"))
                .andExpect(jsonPath("$.receivers[0].payments", hasSize(3)));
    }

    @Test
    void shouldTakeAGiftOffTheTotal() throws Exception {
        setUpEvent();
        // The court is a gift from Coach Jun; P1 bought 300.00 of shuttlecocks.
        postExpense(outsiderExpenseJson("500.00", "Coach Jun", false)).andExpect(status().isCreated());
        addExpense("SHUTTLECOCKS", "300.00", player(1));

        summary()
                .andExpect(jsonPath("$.totalExpenses").value(800.0))
                .andExpect(jsonPath("$.gifts").value(500.0))
                .andExpect(jsonPath("$.toCover").value(300.0))
                .andExpect(jsonPath("$.equalShare").value(100.0))
                // Coach Jun isn't owed anything, so he isn't listed.
                .andExpect(jsonPath("$.balances", hasSize(3)))
                .andExpect(jsonPath("$.balances[0].balance").value(200.0));
    }

    // ---------- NEW: contributions ----------

    @Test
    void shouldTakeContributionsOffBeforeSplitting() throws Exception {
        setUpEvent();
        Long court = addExpense("COURT_RENTAL", "1000.00", player(1));

        // P2 willingly puts in 400 for the court, and doesn't also pay an equal share.
        postContribution("""
                { "participantId": %d, "amount": 400.00, "expenseId": %d, "note": "  For the court  " }
                """.formatted(player(2), court))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participant.playerName").value("P2"))
                .andExpect(jsonPath("$.expenseDescription").value("Something"))
                .andExpect(jsonPath("$.note").value("For the court"));
        setSharing(player(2), "false").andExpect(status().isOk());

        // 1000 - 400 = 600, split between P1 and P3: 300 each.
        summary()
                .andExpect(jsonPath("$.contributionsTotal").value(400.0))
                .andExpect(jsonPath("$.sharedEqually").value(600.0))
                .andExpect(jsonPath("$.sharingParticipants").value(2))
                .andExpect(jsonPath("$.equalShare").value(300.0))
                .andExpect(jsonPath("$.balances[1].person.playerName").value("P2"))
                .andExpect(jsonPath("$.balances[1].contribution").value(400.0))
                .andExpect(jsonPath("$.balances[1].share").value(0.0))
                .andExpect(jsonPath("$.balances[1].owes").value(400.0))
                // P1 receives 1000 - 300 = 700: 400 from P2, 300 from P3.
                .andExpect(jsonPath("$.receivers[0].person.playerName").value("P1"))
                .andExpect(jsonPath("$.receivers[0].total").value(700.0))
                .andExpect(jsonPath("$.receivers[0].payments", hasSize(2)));

        // If P2 also wants to pay an equal share: 600 / 3 = 200 each, P2 owes 600.
        setSharing(player(2), "true").andExpect(status().isOk());
        summary()
                .andExpect(jsonPath("$.equalShare").value(200.0))
                .andExpect(jsonPath("$.balances[1].owes").value(600.0));
    }

    @Test
    void shouldValidateContributions() throws Exception {
        setUpEvent();

        postContribution("{ }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.participantId").value("Choose who contributes."))
                .andExpect(jsonPath("$.errors.amount").value("Amount is required."));

        // A participant and an expense from another event.
        Long otherEvent = createEvent(organizer);
        Long stranger = addParticipant(otherEvent, "Stranger");
        postContribution("{ \"participantId\": %d, \"amount\": 50 }".formatted(stranger))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The contributor must be a participant of this event."));

        String otherExpense = mockMvc.perform(organizer.authorize(post("/api/events/" + otherEvent + "/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Court", "COURT_RENTAL", "100.00", stranger)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        postContribution("{ \"participantId\": %d, \"amount\": 50, \"expenseId\": %d }"
                .formatted(player(1), idOf(otherExpense)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That expense isn't part of this event."));
    }

    @Test
    void shouldEditAndDeleteContributions() throws Exception {
        setUpEvent();
        Long expenseId = addExpense("SHUTTLECOCKS", "800.00", player(1));
        String body = postContribution("{ \"participantId\": %d, \"amount\": 200, \"expenseId\": %d }"
                        .formatted(player(2), expenseId))
                .andReturn().getResponse().getContentAsString();
        String url = eventUrl() + "/contributions/" + idOf(body);

        mockMvc.perform(organizer.authorize(put(url))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"participantId\": %d, \"amount\": 250 }".formatted(player(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250.0))
                .andExpect(jsonPath("$.expenseId").value(nullValue()));

        mockMvc.perform(organizer.authorize(delete(url))).andExpect(status().isNoContent());
        mockMvc.perform(organizer.authorize(get(eventUrl() + "/contributions")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldKeepContributionWhenItsExpenseIsDeleted() throws Exception {
        setUpEvent();
        Long expenseId = addExpense("SHUTTLECOCKS", "800.00", player(1));
        postContribution("{ \"participantId\": %d, \"amount\": 200, \"expenseId\": %d }"
                .formatted(player(2), expenseId))
                .andExpect(status().isCreated());

        mockMvc.perform(organizer.authorize(delete(expensesUrl() + "/" + expenseId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(organizer.authorize(get(eventUrl() + "/contributions")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(200.0))
                .andExpect(jsonPath("$[0].expenseId").value(nullValue()));
    }

    // ---------- NEW: who shares equally ----------

    @Test
    void shouldIncludePlayersWhoLeftAfterPlayingByDefault() throws Exception {
        setUpEvent(5);
        // P1-P4 play a match, and it's completed (faked with SQL: scoring isn't the point here).
        String match = mockMvc.perform(organizer.authorize(post(eventUrl() + "/matches"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"courtNumber\": 1, \"teamA\": [%d, %d], \"teamB\": [%d, %d] }"
                                .formatted(player(1), player(2), player(3), player(4))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        entityManager.flush();
        jdbcTemplate.update("""
                UPDATE matches SET status = 'COMPLETED', winning_team = 'A', started_at = NOW(), completed_at = NOW()
                WHERE id = ?
                """, idOf(match));
        entityManager.clear();

        // P1 played and leaves; P5 leaves without playing.
        removeParticipant(player(1));
        removeParticipant(player(5));

        mockMvc.perform(organizer.authorize(get(expensesUrl() + "/sharing")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].playerName").value("P1"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].playedAMatch").value(true))
                .andExpect(jsonPath("$[0].sharesEqually").value(true))
                .andExpect(jsonPath("$[0].decidedByOrganizer").value(false))
                .andExpect(jsonPath("$[4].playerName").value("P5"))
                .andExpect(jsonPath("$[4].sharesEqually").value(false));

        // The group agrees P1 doesn't pay; later the organizer goes back to the default.
        setSharing(player(1), "false")
                .andExpect(jsonPath("$.sharesEqually").value(false))
                .andExpect(jsonPath("$.decidedByOrganizer").value(true));
        setSharing(player(1), "null")
                .andExpect(jsonPath("$.sharesEqually").value(true))
                .andExpect(jsonPath("$.decidedByOrganizer").value(false));
    }

    // ---------- NEW: rounding ----------

    @Test
    void shouldRoundSharesUp() throws Exception {
        setUpEvent();
        // 100.00 / 3 = 33.34, rounded up to 34.00: 2.00 extra, kept by P1 who paid.
        addExpense("SHUTTLECOCKS", "100.00", player(1));

        mockMvc.perform(organizer.authorize(put(expensesUrl() + "/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"shareRounding\": \"PESO_1\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareRounding").value("PESO_1"))
                .andExpect(jsonPath("$.equalShare").value(34.0))
                .andExpect(jsonPath("$.surplus").value(2.0))
                .andExpect(jsonPath("$.surplusKeptBy.playerName").value("P1"))
                .andExpect(jsonPath("$.balances[0].surplusKept").value(2.0))
                // P1 paid 100, owes 34, keeps 2 -> receives 68 (34 each from P2 and P3).
                .andExpect(jsonPath("$.balances[0].balance").value(68.0));

        // The setting is saved.
        summary().andExpect(jsonPath("$.shareRounding").value("PESO_1"));

        mockMvc.perform(organizer.authorize(put(expensesUrl() + "/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"shareRounding\": \"PESO_2\" }"))
                .andExpect(status().isBadRequest());
    }

    // ---------- security ----------

    @Test
    void shouldNotLetAnotherOrganizerSeeOrChangeExpenses() throws Exception {
        setUpEvent();
        LoggedInUser intruder = AuthTestHelper.registerAndLogin(mockMvc);

        mockMvc.perform(intruder.authorize(get(expensesUrl())))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(get(expensesUrl() + "/summary")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(get(expensesUrl() + "/sharing")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(get(eventUrl() + "/contributions")))
                .andExpect(status().isNotFound());
        mockMvc.perform(intruder.authorize(post(expensesUrl()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Court", "COURT_RENTAL", "100.00", player(1))))
                .andExpect(status().isNotFound());
    }
}