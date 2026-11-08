package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO for updating a task's parent dependencies.
 * Contains the list of parent task IDs that this task will depend on.
 */
@Schema(description = "Request body for replacing a task's upstream (parent) dependencies")
public class UpdateTaskDependenciesRequest {

    @NotNull(message = "Parent task IDs list cannot be null")
    @Schema(description = "Complete list of parent task IDs this task should depend on. Replaces existing dependencies. "
            + "BFS cycle detection is applied before persisting.",
            example = "[1, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> parentTaskIds;

    public UpdateTaskDependenciesRequest() {}

    public UpdateTaskDependenciesRequest(List<Long> parentTaskIds) {
        this.parentTaskIds = parentTaskIds;
    }

    public List<Long> getParentTaskIds() {
        return parentTaskIds;
    }

    public void setParentTaskIds(List<Long> parentTaskIds) {
        this.parentTaskIds = parentTaskIds;
    }
}
