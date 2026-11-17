
package com.smashit_beta.backend.service;

import com.smashit_beta.backend.dto.request.RegisterRequest;
import com.smashit_beta.backend.dto.response.UserResponse;
import com.smashit_beta.backend.entity.User;
import com.smashit_beta.backend.exception.EmailAlreadyRegisteredException;
import com.smashit_beta.backend.exception.UsernameAlreadyRegisteredException;
import com.smashit_beta.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String normalizedUsername = request.username().trim();
        String normalizedFirstName = request.firstName().trim();
        String normalizedLastName = request.lastName().trim();


        String rawPassword = request.password();

        // BCrypt supports a maximum of 72 password bytes.
        int passwordBytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;

        if (passwordBytes > 72) {
            throw new IllegalArgumentException(
                    "Password must not exceed 72 bytes."
            );
        }

        // Check whether the email is already registered.
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(
                    "An account with this email already exists."
            );
        }

        // Reject registration if the username is already in use.
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UsernameAlreadyRegisteredException(
                    "This username is already taken."
            );
        }

        // Hash the password before saving it.
        String passwordHash = passwordEncoder.encode(rawPassword);

        // Create the user entity.
        User user = new User(
                normalizedFirstName,
                normalizedLastName,
                normalizedUsername,
                normalizedEmail,
                passwordHash
        );

        // Save the user to PostgreSQL.
        User savedUser = userRepository.save(user);

        // Convert the entity into a safe response DTO.
        return new UserResponse(
                savedUser.getId(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }
}
