package com.sporty.jackpot.service;

/**
 * Thrown when a request is valid but conflicts with the current state — translated to 409 by
 * {@code ApiExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
