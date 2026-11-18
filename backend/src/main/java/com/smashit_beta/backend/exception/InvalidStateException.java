package com.smashit_beta.backend.exception;

// Thrown when an action isn't allowed in the thing's CURRENT state,
// e.g. editing a completed event, or cancelling an event that's already cancelled.
// GlobalExceptionHandler turns this into HTTP 409 Conflict.
// We'll reuse it later (e.g. "this match is already completed").
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}