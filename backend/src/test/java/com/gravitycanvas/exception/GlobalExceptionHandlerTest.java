package com.gravitycanvas.exception;

import com.gravitycanvas.dto.ErrorResponse;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks");
    }

    @Test
    @DisplayName("Validation exception maps to HTTP 400 with field errors")
    void testMethodArgumentNotValidException() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "createTaskRequest");
        bindingResult.addError(new FieldError("createTaskRequest", "title", "Task title is required"));
        bindingResult.addError(new FieldError("createTaskRequest", "deadline", "Task deadline is required"));

        MethodParameter parameter = new MethodParameter(
            this.getClass().getDeclaredMethod("setUp"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationExceptions(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertEquals(2, response.getBody().getErrors().size());
        assertEquals("Task title is required", response.getBody().getErrors().get("title"));
        assertEquals("Task deadline is required", response.getBody().getErrors().get("deadline"));
        assertEquals("/api/tasks", response.getBody().getPath());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    @DisplayName("ResourceNotFoundException maps to HTTP 404")
    void testResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Task not found with ID: 99");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("Task not found with ID: 99", response.getBody().getMessage());
    }

    @Test
    @DisplayName("CircularDependencyException maps to HTTP 400 Bad Request")
    void testCircularDependencyException() {
        CircularDependencyException ex = new CircularDependencyException("Circular dependency detected: Task #1 cannot depend on Task #3.");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCircularDependency(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("Circular dependency detected: Task #1 cannot depend on Task #3.", response.getBody().getMessage());
        assertEquals("/api/tasks", response.getBody().getPath());
    }

    @Test
    @DisplayName("IllegalArgumentException with 'not found' maps to HTTP 404")
    void testIllegalArgumentWithNotFound() {
        IllegalArgumentException ex = new IllegalArgumentException("User not found: devon.smith");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    @DisplayName("Generic IllegalArgumentException maps to HTTP 400")
    void testIllegalArgumentGeneric() {
        IllegalArgumentException ex = new IllegalArgumentException("Title exceeds maximum allowed length.");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Title exceeds maximum allowed length.", response.getBody().getMessage());
    }

    @Test
    @DisplayName("IllegalStateException maps to HTTP 400")
    void testIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Task cannot transition from DONE to IN_PROGRESS directly.");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalState(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Task cannot transition from DONE to IN_PROGRESS directly.", response.getBody().getMessage());
    }

    @Test
    @DisplayName("AccessDeniedException maps to HTTP 403 Forbidden")
    void testAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Forbidden", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("OptimisticLockException and ObjectOptimisticLockingFailureException map to HTTP 409 Conflict")
    void testOptimisticLockException() {
        OptimisticLockException ex1 = new OptimisticLockException("Row updated elsewhere");
        ObjectOptimisticLockingFailureException ex2 = new ObjectOptimisticLockingFailureException("Task", 1L);

        ResponseEntity<ErrorResponse> response1 = exceptionHandler.handleOptimisticLock(ex1, request);
        ResponseEntity<ErrorResponse> response2 = exceptionHandler.handleOptimisticLock(ex2, request);

        assertEquals(HttpStatus.CONFLICT, response1.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, response2.getStatusCode());
        assertEquals(409, response1.getBody().getStatus());
        assertEquals("Conflict", response1.getBody().getError());
        assertTrue(response1.getBody().getMessage().contains("concurrently"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException maps to HTTP 409 Conflict with sanitized message")
    void testDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique constraint violation on table USERS column USERNAME");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("Database constraint violation"));
        assertFalse(response.getBody().getMessage().contains("table USERS"));
        assertFalse(response.getBody().getMessage().contains("column USERNAME"));
    }

    @Test
    @DisplayName("Generic unhandled exception maps to HTTP 500 without leaking details")
    void testGenericException() {
        NullPointerException ex = new NullPointerException("Internal null pointer in SQL driver connection");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("An unexpected internal server error occurred.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("SQL"));
        assertFalse(response.getBody().getMessage().contains("NullPointerException"));
    }
}
