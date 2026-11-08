package com.gravitycanvas.dto;

import com.gravitycanvas.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating the status of a task.
 */
@Schema(description = "Request body for updating a task's lifecycle status")
public class UpdateStatusRequest {

    @NotNull(message = "Task status is required")
    @Schema(description = "New task status value", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private TaskStatus status;

    public UpdateStatusRequest() {}

    public UpdateStatusRequest(TaskStatus status) {
        this.status = status;
    }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
}
