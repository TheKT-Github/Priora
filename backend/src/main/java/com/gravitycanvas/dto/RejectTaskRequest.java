package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for rejecting a developer task in the manager approvals queue.
 */
@Schema(description = "Request body for rejecting a task pending manager approval")
public class RejectTaskRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
    @Schema(description = "Mandatory explanation of why the task was rejected (max 1000 characters)",
            example = "Task scope exceeds sprint capacity. Please split into smaller subtasks.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String rejectionReason;

    public RejectTaskRequest() {}

    public RejectTaskRequest(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
