
package com.smashit_beta.backend.service;

import java.util.Optional;

import com.smashit_beta.backend.dto.request.RegisterRequest;
import com.smashit_beta.backend.dto.response.UserResponse;
import com.smashit_beta.backend.entity.User;
import com.smashit_beta.backend.exception.EmailAlreadyRegisteredException;
import com.smashit_beta.backend.exception.UsernameAlreadyRegisteredException;
import com.smashit_beta.backend.repository.UserRepository;
import com.smashit_beta.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Runs these tests with Mockito without starting the Spring application.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // Simulates the database repository.
    @Mock
    private UserRepository userRepository;

    // Simulates BCrypt password hashing.
    @Mock
    private PasswordEncoder passwordEncoder;

    // Creates UserService and injects the mocked dependencies.
    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterUserSuccessfully() {

        // Arrange: prepare a registration request with whitespace
        // and uppercase email to verify input normalization.
        RegisterRequest request = new RegisterRequest(
                "  Test  ",
                "  Player  ",
                "  PlayerOne  ",
                " PLAYER@example.com ",
                "SmashHit123!"
        );

        // Simulate an email and username that are not yet registered.
        when(userRepository.existsByEmailIgnoreCase("player@example.com"))
                .thenReturn(false);

        when(userRepository.existsByUsernameIgnoreCase("PlayerOne"))
                .thenReturn(false);

        // Simulate BCrypt returning a hashed password.
        when(passwordEncoder.encode("SmashHit123!"))
                .thenReturn("bcrypt-test-hash");

        // Simulate saving the user by returning the supplied entity.
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: execute the registration operation.
        UserResponse response = userService.register(request);

        // Capture the actual User entity passed to the repository.
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        // Assert: verify that the user's information was normalized.
        assertEquals("Test", savedUser.getFirstName());
        assertEquals("Player", savedUser.getLastName());
        assertEquals("PlayerOne", savedUser.getUsername());
        assertEquals("player@example.com", savedUser.getEmail());

        // Verify that the hashed password was stored, not the raw password.
        assertEquals("bcrypt-test-hash", savedUser.getPasswordHash());
        assertNotEquals("SmashHit123!", savedUser.getPasswordHash());

        // Verify that the response contains the correct public information.
        assertEquals("Test", response.firstName());
        assertEquals("Player", response.lastName());
        assertEquals("PlayerOne", response.username());
        assertEquals("player@example.com", response.email());

        // Verify that password encoding actually occurred.
        verify(passwordEncoder).encode("SmashHit123!");
    }

    @Test
    void shouldRejectAlreadyRegisteredEmail() {

        RegisterRequest request = new RegisterRequest(
                "Test",
                "Player",
                "PlayerOne",
                "player@example.com",
                "SmashHit123!"
        );

        // Simulate an email that already exists.
        when(userRepository.existsByEmailIgnoreCase("player@example.com"))
                .thenReturn(true);

        // Registration should throw our custom email exception.
        assertThrows(
                EmailAlreadyRegisteredException.class,
                () -> userService.register(request)
        );

        // No user should be saved or password hashed.
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());

        // Username checking should not be reached after duplicate email.
        verify(userRepository, never()).existsByUsernameIgnoreCase(anyString());
    }

    @Test
    void shouldRejectAlreadyRegisteredUsername() {

        RegisterRequest request = new RegisterRequest(
                "Another",
                "Player",
                "PlayerOne",
                "another@example.com",
                "SmashHit123!"
        );

        // Simulate an email that is available.
        when(userRepository.existsByEmailIgnoreCase("another@example.com"))
                .thenReturn(false);

        // Simulate a username that is already taken.
        when(userRepository.existsByUsernameIgnoreCase("PlayerOne"))
                .thenReturn(true);

        // Registration should throw our custom username exception.
        assertThrows(
                UsernameAlreadyRegisteredException.class,
                () -> userService.register(request)
        );

        // No user should be saved or password hashed.
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void shouldRejectPasswordExceeding72Bytes() {

        // Create a password containing 73 ASCII bytes.
        String longPassword = "a".repeat(73);

        RegisterRequest request = new RegisterRequest(
                "Test",
                "Player",
                "PlayerOne",
                "player@example.com",
                longPassword
        );

        // Registration should reject the oversized password.
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        // The repository and encoder should never be accessed.
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectPasswordExceeding72Utf8Bytes() {

        // Each "é" occupies two bytes in UTF-8.
        // 37 characters therefore occupy 74 bytes.
        String longPassword = "é".repeat(37);

        RegisterRequest request = new RegisterRequest(
                "Test",
                "Player",
                "PlayerOne",
                "player@example.com",
                longPassword
        );

        // Although the character count is below 72,
        // the UTF-8 byte count exceeds BCrypt's limit.
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(request)
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

        @Test
    void shouldReturnUserByEmail() {

        // Arrange: a user that the (fake) repository will "find".
        User user = new User(
                "Test",
                "Player",
                "PlayerOne",
                "player@example.com",
                "bcrypt-test-hash"
        );

        // Whitespace is trimmed before the lookup.
        when(userRepository.findByEmailIgnoreCase("player@example.com"))
                .thenReturn(Optional.of(user));

        // Act
        UserResponse response = userService.getByEmail("  player@example.com  ");

        // Assert: the public fields are copied into the response.
        assertEquals("PlayerOne", response.username());
        assertEquals("player@example.com", response.email());
    }

    @Test
    void shouldThrowWhenUserEmailNotFound() {

        // Optional.empty() simulates "no row in the database".
        when(userRepository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getByEmail("missing@example.com")
        );
    }
}
