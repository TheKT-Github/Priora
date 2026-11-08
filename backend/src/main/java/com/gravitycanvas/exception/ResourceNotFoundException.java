package com.gravitycanvas.exception;

/**
 * Exception thrown when a requested resource (such as Task or UserAccount) cannot be found.
 * Mapped to HTTP 404 NOT FOUND by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier));
    }
}
