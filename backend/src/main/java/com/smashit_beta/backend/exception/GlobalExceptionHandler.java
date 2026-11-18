package com.smashit_beta.backend.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

// @RestControllerAdvice = "watch every controller; if one throws an exception,
// look for a matching @ExceptionHandler method here and use its return value
// as the HTTP response instead of a generic 500 error."
//
// ProblemDetail is Spring's built-in error format (standard RFC 9457). It turns into JSON like:
// { "status": 409, "title": "Conflict", "detail": "This username is already taken." }
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Duplicate email or username -> 409 Conflict.
    @ExceptionHandler({
            EmailAlreadyRegisteredException.class,
            UsernameAlreadyRegisteredException.class
    })
    public ProblemDetail handleAlreadyRegistered(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Safety net: if two people register the same email at the exact same moment,
    // both may pass the "exists?" check, and the database unique index rejects the second.
    // We don't expose the raw database message (it reveals table/index names).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data."
        );
    }

    // @Valid failed (e.g. blank email, short password) -> 400 Bad Request,
    // with a field-by-field list the frontend can show under each input:
    // "errors": { "email": "Please provide a valid email address." }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields are invalid."
        );

        // LinkedHashMap keeps the fields in the order they were found.
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // putIfAbsent: if a field breaks two rules, keep only the first message.
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        // setProperty adds an extra key to the JSON response.
        problem.setProperty("errors", errors);
        return problem;
    }

    // The request body couldn't be read: broken JSON, or a value of the wrong type,
    // e.g. "matchFormat": "BEST_OF_FIVE" or "startsAt": "tomorrow" -> 400 Bad Request.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request body is malformed or contains an invalid value."
        );
    }

    // Business-rule violations thrown by our services, e.g. the 72-byte password check.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // The action isn't allowed in the current state
    // (e.g. editing a completed event) -> 409 Conflict.
    @ExceptionHandler(InvalidStateException.class)
    public ProblemDetail handleInvalidState(InvalidStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // A URL value couldn't be converted to the expected type,
    // e.g. /api/events?status=FINISHED or /api/events/abc -> 400 Bad Request.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value for '" + ex.getName() + "'."
        );
    }

    // Creating/renaming would duplicate something (e.g. a participant name) -> 409.
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Something requested doesn't exist -> 404 Not Found.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Wrong email or password during login -> 401 Unauthorized.
    // We deliberately use ONE message for both cases so attackers
    // can't use the login form to discover which emails are registered.
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password."
        );
    }
}