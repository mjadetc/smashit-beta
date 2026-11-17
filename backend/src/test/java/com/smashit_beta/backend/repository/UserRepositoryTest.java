
package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveUser() {

        User user = new User(
                "Test",
                "Player 1",
                "Test Player",
                "testplayer@example.com",
                "test-hash"
        );

        User savedUser = userRepository.saveAndFlush(user);

        assertNotNull(savedUser.getId());
        assertEquals("Test Player", savedUser.getUsername());
        assertEquals("testplayer@example.com", savedUser.getEmail());
        assertNotNull(savedUser.getCreatedAt());
        assertNotNull(savedUser.getUpdatedAt());
    }

    @Test
    void shouldFindUserByEmailIgnoringCase() {

        String email = "findtest-" + UUID.randomUUID() + "@example.com";

        User user = new User(
                "Test",
                "Player 2",
                "Find Test",
                email,
                "test-hash"
        );

        userRepository.saveAndFlush(user);

        var result = userRepository.findByEmailIgnoreCase(
                email.toUpperCase()
        );

        assertTrue(result.isPresent());
        assertEquals(email, result.get().getEmail());
    }

    @Test
    void shouldCheckIfEmailExists() {

        String email = "existstest-" + UUID.randomUUID() + "@example.com";

        User user = new User(
                "Test",
                "Player 3",
                "Exists Test",
                email,
                "test-hash"
        );

        userRepository.saveAndFlush(user);

        assertTrue(userRepository.existsByEmailIgnoreCase(email));
        assertFalse(userRepository.existsByEmailIgnoreCase(
                "unknown-" + UUID.randomUUID() + "@example.com"
        ));
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() {

        String email = "duplicate-" + UUID.randomUUID() + "@example.com";

        User firstUser = new User(
                "Test",
                "Player 1",
                "First Player",
                email,
                "test-hash"
        );

        User secondUser = new User(
                "Test",
                "Player 2",
                "Second Player",
                email.toUpperCase(),
                "test-hash"
        );

        userRepository.saveAndFlush(firstUser);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(secondUser)
        );
    }


        @Test
        void shouldFindUserByUsernameIgnoringCase() {

        // Generate a unique username to avoid conflicts with other tests.
        String username = "player_" + UUID.randomUUID()
                .toString().substring(0, 8);

        // Create a test user using the updated User constructor.
        User user = new User(
                "Test",                 // firstName
                "Player",               // lastName
                username,                       // username
                username + "@example.com",
                "test-hash"
        );

        // Save the user to the test database.
        userRepository.saveAndFlush(user);

        // Search using uppercase to verify case-insensitive lookup.
        var result = userRepository.findByUsernameIgnoreCase(
                username.toUpperCase()
        );

        assertTrue(result.isPresent());
        assertEquals(username, result.get().getUsername());
        }

        @Test
        void shouldCheckIfUsernameExistsIgnoringCase() {

        // Create a unique username for this test.
        String username = "exists_" + UUID.randomUUID()
                .toString().substring(0, 8);

        User user = new User(
                "Test",
                "Player",
                username,
                username + "@example.com",
                "test-hash"
        );

        userRepository.saveAndFlush(user);

        // Verify that username existence checks ignore letter casing.
        assertTrue(
                userRepository.existsByUsernameIgnoreCase(
                        username.toUpperCase()
                )
        );

        // Verify that a nonexistent username returns false.
        assertFalse(
                userRepository.existsByUsernameIgnoreCase(
                        "unknown_" + UUID.randomUUID()
                )
        );
        }

        @Test
        void shouldRejectDuplicateUsernameIgnoringCase() {

        // Both usernames are the same except for letter casing.
        String username = "duplicate_" + UUID.randomUUID()
                .toString().substring(0, 8);

        User firstUser = new User(
                "First",
                "Player",
                username,
                "first_" + username + "@example.com",
                "test-hash"
        );

        User secondUser = new User(
                "Second",
                "Player",
                username.toUpperCase(),
                "second_" + username + "@example.com",
                "test-hash"
        );

        // Save the first user.
        userRepository.saveAndFlush(firstUser);

        // The database unique index should reject the second user.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(secondUser)
        );
        }

}
