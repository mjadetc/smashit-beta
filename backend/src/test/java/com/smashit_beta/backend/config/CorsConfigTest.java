package com.smashit_beta.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// NEW in step 8: the allowed origins come from a setting, so Render can allow the
// Vercel address without changing code.
// properties = "..." sets a value for this test only, as if it were in
// application.properties (or the CORS_ALLOWED_ORIGINS environment variable).
@SpringBootTest(properties = "app.cors.allowed-origins=https://smashit-beta.vercel.app,http://localhost:5173")
class CorsConfigTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void shouldReadTheAllowedOriginsFromTheSettings() {
        // Any request path works: the same rules apply to all of them ("/**").
        CorsConfiguration configuration =
                corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/events"));

        assertEquals(List.of("https://smashit-beta.vercel.app", "http://localhost:5173"),
                configuration.getAllowedOrigins());
    }
}