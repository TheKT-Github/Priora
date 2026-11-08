package com.gravitycanvas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized API Error Response DTO for Priora REST services.
 * Ensures consistent structure across validation errors, client errors, and system failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error envelope returned by all Priora API error responses")
public class ErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(description = "UTC timestamp of when the error occurred", example = "2025-11-01T10:30:00Z")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Short HTTP error phrase", example = "Bad Request")
    private String error;

    @Schema(description = "Human-readable error message", example = "Validation failed for one or more fields")
    private String message;

    @Schema(description = "Field-level validation error map (field name → error message). Present only for 400 validation errors.",
            nullable = true, example = "{\"title\": \"must not be blank\"}")
    private Map<String, String> errors;

    @Schema(description = "Request URI that triggered the error", example = "/api/tasks")
    private String path;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ErrorResponse(int status, String error, String message, Map<String, String> errors, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.errors = errors;
        this.path = path;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, String> getErrors() { return errors; }
    public void setErrors(Map<String, String> errors) { this.errors = errors; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}

