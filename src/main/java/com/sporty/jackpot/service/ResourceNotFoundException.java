package com.sporty.jackpot.service;

/**
 * Thrown when a client asks about something that does not exist — translated to 404 by
 * {@code ApiExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
