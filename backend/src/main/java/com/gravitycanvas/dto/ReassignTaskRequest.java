package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/**
 * DTO for reassigning a task to a developer.
 */
@Schema(description = "Request body for reassigning a task to a different user")
public class ReassignTaskRequest {

    @Positive(message = "Assignee ID must be a positive number")
    @Schema(description = "ID of the user to whom the task should be reassigned", example = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long assigneeId;

    public ReassignTaskRequest() {}

    public ReassignTaskRequest(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
}
