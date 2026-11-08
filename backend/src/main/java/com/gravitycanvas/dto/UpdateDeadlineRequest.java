package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * DTO for extending or modifying a task deadline.
 */
@Schema(description = "Request body for updating a task's deadline")
public class UpdateDeadlineRequest {

    @NotNull(message = "Task deadline is required")
    @Schema(description = "New deadline in ISO-8601 date-time format", example = "2025-12-31T23:59:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime deadline;

    public UpdateDeadlineRequest() {}

    public UpdateDeadlineRequest(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
}
