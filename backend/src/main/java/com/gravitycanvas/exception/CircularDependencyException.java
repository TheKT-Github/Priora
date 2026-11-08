package com.gravitycanvas.exception;

/**
 * Exception thrown when a proposed task dependency relationship would introduce
 * a direct or indirect cycle or self-dependency in the Directed Acyclic Graph (DAG).
 */
public class CircularDependencyException extends RuntimeException {

    public CircularDependencyException(String message) {
        super(message);
    }

    public CircularDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
