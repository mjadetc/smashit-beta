package com.smashit_beta.backend.exception;

// Thrown when something the client asked for does not exist.
// GlobalExceptionHandler turns this into an HTTP 404 Not Found.
// We will reuse this later for events, participants, matches, and so on.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}