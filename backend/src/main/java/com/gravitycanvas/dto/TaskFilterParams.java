package com.gravitycanvas.dto;

import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Query parameters for task catalog filtering, searching, and sorting")
public class TaskFilterParams {

    @Schema(description = "Filter by task status (TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED)", nullable = true, example = "IN_PROGRESS")
    private TaskStatus status;

    @Schema(description = "Filter by target environment (PRODUCTION, STAGING, DEVELOPMENT)", nullable = true, example = "PRODUCTION")
    private TaskEnvironment environment;

    @Schema(description = "Filter by assigned user primary key ID", nullable = true, example = "2")
    private Long assigneeId;

    @Schema(description = "Case-insensitive substring search across task title", nullable = true, example = "Security")
    private String search;

    @Schema(description = "Field to sort by (createdAt, deadline, status, title, environment)", defaultValue = "createdAt", example = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Sort direction (asc, desc)", defaultValue = "desc", example = "desc")
    private String sortDir = "desc";

    public TaskFilterParams() {}

    public TaskFilterParams(TaskStatus status, TaskEnvironment environment, Long assigneeId, String search, String sortBy, String sortDir) {
        this.status = status;
        this.environment = environment;
        this.assigneeId = assigneeId;
        this.search = search;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            this.sortBy = sortBy.trim();
        }
        if (sortDir != null && !sortDir.trim().isEmpty()) {
            this.sortDir = sortDir.trim().toLowerCase();
        }
    }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskEnvironment getEnvironment() { return environment; }
    public void setEnvironment(TaskEnvironment environment) { this.environment = environment; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { 
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            this.sortBy = sortBy.trim();
        }
    }

    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { 
        if (sortDir != null && !sortDir.trim().isEmpty()) {
            this.sortDir = sortDir.trim().toLowerCase();
        }
    }
}
