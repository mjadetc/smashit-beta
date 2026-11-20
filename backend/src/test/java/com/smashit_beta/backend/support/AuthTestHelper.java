package com.smashit_beta.backend.support;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Reusable test helper: "give me a registered, logged-in organizer".
// Every feature from now on (events, participants, matches...) needs a logged-in user
// in its integration tests, so this lives in one place instead of being copy-pasted.
public final class AuthTestHelper {

    // "final" class + private constructor = a utility class nobody can create or extend.
    private AuthTestHelper() {
    }

    // A logged-in user as the tests see it: the SESSION cookie (= the login)
    // and the CSRF cookie needed for POST/PUT/DELETE.
    // A record is perfect for bundling a few values together.
    // CHANGED in step 8: sessions are kept by Spring Session (in PostgreSQL), which finds
    // them by the SESSION cookie, so the tests send that cookie, exactly like a browser.
    public record LoggedInUser(String email, Cookie sessionCookie, Cookie csrfCookie) {

        // Adds the session cookie + CSRF cookie + CSRF header to any request.
        // Usage: mockMvc.perform(user.authorize(post("/api/events")) ...)
        public MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request) {
            return request
                    .cookie(sessionCookie, csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue());
        }
    }

    // Registers a brand-new user (unique email each time) and logs them in.
    public static LoggedInUser registerAndLogin(MockMvc mockMvc) throws Exception {
        // 1. Get the CSRF cookie, like the React app does on startup.
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        // 2. Register.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "organizer_" + suffix + "@example.com";
        String password = "SmashHit123!";

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Test",
                                  "lastName": "Organizer",
                                  "username": "organizer_%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(suffix, email, password)))
                .andExpect(status().isCreated());

        // 3. Log in and keep the session.
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        // The login answer sets the SESSION cookie; we keep it like a browser would.
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");

        return new LoggedInUser(email, sessionCookie, csrfCookie);
    }
}