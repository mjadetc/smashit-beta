
package com.smashit_beta.backend.exception;

// Represents an attempt to register with an already-used username.
public class UsernameAlreadyRegisteredException extends RuntimeException {

    public UsernameAlreadyRegisteredException(String message) {
        super(message);
    }
}
