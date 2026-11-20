
package com.smashit_beta.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionCookieConfigTest {

    @Autowired
    private Environment environment;

    @Test
    void shouldLoadSessionCookieSecuritySettings() {

        // Verify that the session cookie cannot be accessed by JavaScript.
        assertEquals(
                "true",
                environment.getProperty(
                        "server.servlet.session.cookie.http-only"
                )
        );

        // Verify that HTTP is allowed for local development.
        assertEquals(
                "false",
                environment.getProperty(
                        "server.servlet.session.cookie.secure"
                )
        );

        // Verify the configured SameSite policy.
        assertEquals(
                "lax",
                environment.getProperty(
                        "server.servlet.session.cookie.same-site"
                )
        );

        
        // NEW in step 8: an organizer stays logged in through a long evening.
        assertEquals(
                "12h",
                environment.getProperty(
                        "server.servlet.session.timeout"
                )
        );
    }
}
