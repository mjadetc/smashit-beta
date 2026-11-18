package com.smashit_beta.backend.exception;

// Thrown when creating or renaming something would duplicate an existing one,
// e.g. two participants called "Juan" in the same event.
// GlobalExceptionHandler turns this into HTTP 409 Conflict.
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}