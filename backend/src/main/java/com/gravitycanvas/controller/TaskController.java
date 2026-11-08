package com.gravitycanvas.controller;

import com.gravitycanvas.dto.*;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing Task operations and Developer Ticket Authorization Workflows.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * GET /api/tasks
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER
     */
    @Tag(name = "Task Management", description = "Core task lifecycle, canvas retrieval with Dynamic Gravity Scores, status transitions, and blocker management.")
    @Operation(
        summary = "Get all approved tasks with live Dynamic Gravity Scores",
        description = "Retrieves all approved tasks evaluated with dynamic Gravity Scores, sorted descending by score for the main 3-zone canvas. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "List of approved tasks with computed gravity metrics",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TaskResponseDto.class)))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping
    public ResponseEntity<List<TaskResponseDto>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    /**
     * GET /api/tasks/search
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Search and filter approved tasks with pagination",
        description = "Retrieves a paginated catalog of approved tasks with multi-field filtering (status, environment, assignee, title substring) and allowlisted sorting. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Paginated catalog of approved tasks",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation Error - Invalid page, size, sort field, or offset overflow",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/search")
    public ResponseEntity<PageResponse<TaskResponseDto>> searchTasks(
        @Parameter(description = "0-based page index") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size (1 to 100)") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Field to sort by (createdAt, deadline, status, title, environment)") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction (asc, desc)") @RequestParam(defaultValue = "desc") String sortDir,
        @Parameter(description = "Optional sort direction alias (asc, desc)") @RequestParam(required = false) String direction,
        @Parameter(description = "Optional status filter") @RequestParam(required = false) TaskStatus status,
        @Parameter(description = "Optional environment filter") @RequestParam(required = false) TaskEnvironment environment,
        @Parameter(description = "Optional assignee user ID filter") @RequestParam(required = false) Long assigneeId,
        @Parameter(description = "Optional case-insensitive title search string") @RequestParam(required = false) String search
    ) {
        String effectiveSortDir = (direction != null && !direction.trim().isEmpty()) ? direction.trim() : sortDir;
        TaskFilterParams params = new TaskFilterParams(status, environment, assigneeId, search, sortBy, effectiveSortDir);
        PageResponse<TaskResponseDto> response = taskService.searchTasks(params, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/tasks/pending
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Approval Workflow", description = "Manager authorization queue for developer-submitted tickets.")
    @Operation(
        summary = "Get pending tasks awaiting manager approval",
        description = "Retrieves all developer-submitted tasks awaiting manager authorization, sorted newest first. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "List of pending tasks",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TaskResponseDto.class)))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/pending")
    public ResponseEntity<List<TaskResponseDto>> getPendingTasks() {
        return ResponseEntity.ok(taskService.getPendingTasks());
    }

    /**
     * GET /api/tasks/pending/search
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Approval Workflow")
    @Operation(
        summary = "Search and paginate pending developer tickets",
        description = "Retrieves a paginated page of developer-submitted tickets awaiting manager authorization. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Paginated list of pending tasks",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation Error - Invalid page, size, sort field, or offset overflow",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/pending/search")
    public ResponseEntity<PageResponse<TaskResponseDto>> searchPendingTasks(
        @Parameter(description = "0-based page index") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size (1 to 100)") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Field to sort by (createdAt, deadline, status, title, environment)") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction (asc, desc)") @RequestParam(defaultValue = "desc") String sortDir,
        @Parameter(description = "Optional sort direction alias (asc, desc)") @RequestParam(required = false) String direction,
        @Parameter(description = "Optional environment filter") @RequestParam(required = false) TaskEnvironment environment,
        @Parameter(description = "Optional assignee user ID filter") @RequestParam(required = false) Long assigneeId,
        @Parameter(description = "Optional case-insensitive title search string") @RequestParam(required = false) String search
    ) {
        String effectiveSortDir = (direction != null && !direction.trim().isEmpty()) ? direction.trim() : sortDir;
        PageResponse<TaskResponseDto> response = taskService.searchPendingTasks(page, size, sortBy, effectiveSortDir, environment, assigneeId, search);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/tasks
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Create a new task",
        description = "Creates a new task. Manager-created tasks are auto-approved. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Task created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation Error - Invalid title, deadline in past, or invalid environment",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping
    public ResponseEntity<TaskResponseDto> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponseDto created = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/tasks/{id}/approve
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Approval Workflow")
    @Operation(
        summary = "Approve a pending developer task",
        description = "Authorizes a developer-submitted task, transitioning approval status to APPROVED and placing it on the active canvas. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Task approved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Not Found - Task with specified ID does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PutMapping("/{id}/approve")
    public ResponseEntity<TaskResponseDto> approveTask(
            @Parameter(description = "ID of task to approve", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(taskService.approveTask(id));
    }

    /**
     * PUT /api/tasks/{id}/reject
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Approval Workflow")
    @Operation(
        summary = "Reject a pending developer task",
        description = "Rejects a developer-submitted task with an optional reason note. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Task rejected successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Not Found - Task not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PutMapping("/{id}/reject")
    public ResponseEntity<TaskResponseDto> rejectTask(
            @Parameter(description = "ID of task to reject", example = "1") @PathVariable Long id,
            @Valid @RequestBody(required = false) RejectTaskRequest request) {
        return ResponseEntity.ok(taskService.rejectTask(id, request));
    }

    /**
     * DELETE /api/tasks/{id}
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Delete a task",
        description = "Permanently deletes a task. Performs bidirectional dependency unlinking before deletion to prevent orphaned foreign keys. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Task deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Requires Project Manager or Team Lead role"),
        @ApiResponse(responseCode = "404", description = "Not Found - Task not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "ID of task to delete", example = "1") @PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/tasks/{id}/deadline
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Update task deadline",
        description = "Adjusts task deadline and recalculates Sigmoid deadline urgency score. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Deadline updated successfully", content = @Content(schema = @Schema(implementation = TaskResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid deadline", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PutMapping("/{id}/deadline")
    public ResponseEntity<TaskResponseDto> updateDeadline(
            @Parameter(description = "ID of task to update", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateDeadlineRequest request) {
        TaskResponseDto updated = taskService.updateDeadline(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/tasks/{id}/reassign
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Reassign task assignee",
        description = "Assigns task to a new user account (or unassigns if null) and updates assignee workload scores. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task reassigned successfully", content = @Content(schema = @Schema(implementation = TaskResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Task or User not found")
    })
    @PutMapping("/{id}/reassign")
    public ResponseEntity<TaskResponseDto> reassignTask(
            @Parameter(description = "ID of task to reassign", example = "1") @PathVariable Long id,
            @Valid @RequestBody ReassignTaskRequest request) {
        TaskResponseDto updated = taskService.reassignTask(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/tasks/{id}/status
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Update task status",
        description = "Transitions task status (TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED). Tasks marked DONE are automatically forced to ORBIT zone. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER]"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated successfully", content = @Content(schema = @Schema(implementation = TaskResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid status enum", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<TaskResponseDto> updateStatus(
            @Parameter(description = "ID of task to update", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        TaskResponseDto updated = taskService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/tasks/{id}/ping
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     */
    @Tag(name = "Task Management")
    @Operation(
        summary = "Toggle blocker ping alert",
        description = "Toggles the pinged blocker alert state and increments ping escalation counter. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ping toggled successfully", content = @Content(schema = @Schema(implementation = TaskResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PostMapping("/{id}/ping")
    public ResponseEntity<TaskResponseDto> pingBlocker(
            @Parameter(description = "ID of task to ping", example = "1") @PathVariable Long id) {
        TaskResponseDto updated = taskService.pingBlocker(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/tasks/{id}/dependencies
     * Allowed Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD
     * Replaces parent dependencies for a task with strict DAG cycle detection.
     */
    @Tag(name = "Task Dependencies", description = "Directed Acyclic Graph (DAG) dependency management with BFS cycle prevention.")
    @Operation(
        summary = "Update parent dependencies with DAG cycle prevention",
        description = "Replaces the parent dependencies for a task. Validates the proposed graph using BFS reachability; rejects self-dependencies and circular dependencies with HTTP 400. [Required Roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD]"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Dependencies updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Circular dependency or self-dependency detected",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires Project Manager or Team Lead role",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Not Found - Target task or parent task ID does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PutMapping("/{id}/dependencies")
    public ResponseEntity<TaskResponseDto> updateDependencies(
            @Parameter(description = "ID of dependent task", example = "2") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskDependenciesRequest request) {
        TaskResponseDto updated = taskService.updateTaskDependencies(id, request);
        return ResponseEntity.ok(updated);
    }
}
