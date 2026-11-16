
package com.smashit_beta.backend.repository;

import com.smashit_beta.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find an account using its email, ignoring uppercase/lowercase differences.
    Optional<User> findByEmailIgnoreCase(String email);

    // Check whether an email is already registered.
    boolean existsByEmailIgnoreCase(String email);

    // Find an account using its username, ignoring uppercase/lowercase differences.
    Optional<User> findByUsernameIgnoreCase(String username);

    // Check whether a username is already taken.
    boolean existsByUsernameIgnoreCase(String username);
}
