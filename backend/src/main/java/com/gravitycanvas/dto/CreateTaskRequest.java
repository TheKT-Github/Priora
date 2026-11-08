package com.gravitycanvas.dto;

import com.gravitycanvas.entity.TaskEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating a new task card.
 * Enforces title, description, environment, and deadline validation constraints.
 */
@Schema(description = "Payload for creating a new task card")
public class CreateTaskRequest {

    @Schema(description = "Title of the task", example = "Implement OAuth2 Token Revocation", maxLength = 150, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Task title is required")
    @Size(max = 150, message = "Task title must not exceed 150 characters")
    private String title;

    @Schema(description = "Detailed description of the task requirements", example = "Integrate RFC 7009 token revocation endpoint for logout", maxLength = 1000)
    @Size(max = 1000, message = "Task description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Deployment target environment (DEVELOPMENT, STAGING, PRODUCTION)", example = "PRODUCTION", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Target environment is required")
    private TaskEnvironment environment;

    @Schema(description = "User ID of the assigned team member", example = "2")
    @Positive(message = "Assignee ID must be a positive number")
    private Long assigneeId;

    @Schema(description = "Task delivery deadline timestamp", example = "2026-12-31T18:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Task deadline is required")
    private LocalDateTime deadline;

    @Schema(description = "Optional list of parent task IDs this task depends upon", example = "[1, 3]")
    private List<Long> parentTaskIds;

    @Schema(description = "Whether the custom manager risk weight factor is applied", example = "false")
    private boolean customFactorApplied;

    public CreateTaskRequest() {}

    public CreateTaskRequest(String title, String description, TaskEnvironment environment,
                             Long assigneeId, LocalDateTime deadline, List<Long> parentTaskIds,
                             boolean customFactorApplied) {
        this.title = title;
        this.description = description;
        this.environment = environment;
        this.assigneeId = assigneeId;
        this.deadline = deadline;
        this.parentTaskIds = parentTaskIds;
        this.customFactorApplied = customFactorApplied;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskEnvironment getEnvironment() { return environment; }
    public void setEnvironment(TaskEnvironment environment) { this.environment = environment; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public List<Long> getParentTaskIds() { return parentTaskIds; }
    public void setParentTaskIds(List<Long> parentTaskIds) { this.parentTaskIds = parentTaskIds; }

    public boolean isCustomFactorApplied() { return customFactorApplied; }
    public void setCustomFactorApplied(boolean customFactorApplied) { this.customFactorApplied = customFactorApplied; }
}
