package com.smashit_beta.backend.controller;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// INTEGRATION test: unlike UserServiceTest (which fakes the database with Mockito),
// this one starts the WHOLE application: security filters, controllers, services,
// and the real PostgreSQL database.
//
// @SpringBootTest        -> start the full Spring application.
// @AutoConfigureMockMvc  -> give us MockMvc, a fake browser that sends HTTP requests
//                           to our app without starting a real web server.
// @Transactional         -> every test runs in a database transaction that is ROLLED BACK
//                           at the end, so test users never stay in your database.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    // The fake browser.
    @Autowired
    private MockMvc mockMvc;

    // NEW in step 8: plain SQL access, to look inside Spring Session's table.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- helper methods ----------

    // Does what React will do on startup: call /csrf and keep the XSRF-TOKEN cookie.
    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie, "Expected the XSRF-TOKEN cookie to be set");
        return csrfCookie;
    }

    // Builds a register JSON body. """ ... """ is a Java "text block" (a multi-line string),
    // and .formatted(...) fills in each %s, like printf.
    private String registerJson(String username, String email, String password) {
        return """
                {
                  "firstName": "Test",
                  "lastName": "Player",
                  "username": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(username, email, password);
    }

    // Registers a user and returns its email. UUIDs make each username/email unique.
    private String registerUser(Cookie csrf, String password) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "auth_" + suffix + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf)                              // send the cookie back...
                        .header("X-XSRF-TOKEN", csrf.getValue())   // ...and copy it into the header
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("user_" + suffix, email, password)))
                .andExpect(status().isCreated());

        return email;
    }

    // Logs in and returns the SESSION cookie, like the browser keeps it.
    // CHANGED in step 8: Spring Session finds the session by this cookie.
    private Cookie login(Cookie csrf, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        Cookie sessionCookie = result.getResponse().getCookie("SESSION");

        assertNotNull(sessionCookie, "Expected the SESSION cookie to be set");
        return sessionCookie;
    }

    // ---------- tests ----------

    @Test
    void shouldRegisterUser() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("user_" + suffix,
                                "REG_" + suffix + "@Example.com", "SmashHit123!")))
                .andExpect(status().isCreated())
                // jsonPath("$.field") reads a field from the JSON response.
                .andExpect(jsonPath("$.id").isNumber())
                // The email was normalized to lowercase.
                .andExpect(jsonPath("$.email").value("reg_" + suffix + "@example.com"))
                // The password hash must never appear in a response.
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectRegisterWithoutCsrfToken() throws Exception {
        // No cookie and no header -> Spring Security blocks the request.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("nocsrf", "nocsrf@example.com", "SmashHit123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnFieldErrorsForInvalidRegistration() throws Exception {
        Cookie csrf = fetchCsrfCookie();

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("someone", "not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email")
                        .value("Please provide a valid email address."))
                .andExpect(jsonPath("$.errors.password")
                        .value("Password must be between 8 and 72 characters."));
    }

    @Test
    void shouldReturnConflictForDuplicateEmail() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        String email = registerUser(csrf, "SmashHit123!");

        // Same email (different casing), different username.
        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("other_" + UUID.randomUUID().toString().substring(0, 8),
                                email.toUpperCase(), "SmashHit123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("An account with this email already exists."));
    }

    @Test
    void shouldLoginAndReturnCurrentUser() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        String email = registerUser(csrf, "SmashHit123!");

        Cookie session = login(csrf, email, "SmashHit123!");

        // Sending the session cookie proves the login was remembered between requests.
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    // NEW in step 8: the login is stored in PostgreSQL, so it survives a server restart.
    @Test
    void shouldStoreTheLoginSessionInTheDatabase() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        String email = registerUser(csrf, "SmashHit123!");

        login(csrf, email, "SmashHit123!");

        // Spring Session writes who is logged in into PRINCIPAL_NAME.
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Integer.class, email);
        assertEquals(1, sessions);
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        String email = registerUser(csrf, "SmashHit123!");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "WrongPassword!" }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void shouldReturnUnauthorizedForMeWhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogout() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        String email = registerUser(csrf, "SmashHit123!");
        Cookie session = login(csrf, email, "SmashHit123!");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(session, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        // After logout, the old session cookie no longer counts as logged in.
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }
}