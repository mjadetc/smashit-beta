
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
        assertEquals("Test Player", savedUser.getUserName());
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
}
