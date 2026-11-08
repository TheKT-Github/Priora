package com.gravitycanvas.dto;

import com.gravitycanvas.entity.ApprovalStatus;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Full task response including identity, metadata, dependency graph IDs, and Gravity Engine calculation breakdown")
public class TaskResponseDto {
    @Schema(description = "Task primary key", example = "42")
    private Long id;
    @Schema(description = "Short task title", example = "Implement OAuth2 login")
    private String title;
    @Schema(description = "Detailed task description", example = "Integrate Google OAuth2 using Spring Security")
    private String description;
    @Schema(description = "Current lifecycle status", example = "IN_PROGRESS")
    private TaskStatus status;
    @Schema(description = "Manager approval status (relevant only for TEAM_MEMBER tasks when approval is required)", example = "APPROVED")
    private ApprovalStatus approvalStatus;
    @Schema(description = "Manager's explanation if the task was rejected", nullable = true)
    private String rejectionReason;
    @Schema(description = "Deployment environment affecting the gravity multiplier", example = "PRODUCTION")
    private TaskEnvironment environment;
    @Schema(description = "Whether the manager's custom bonus factor has been applied to this task", example = "false")
    private boolean customFactorApplied;
    @Schema(description = "Whether the task has been pinged by the assignee for manager attention", example = "false")
    private boolean pinged;
    @Schema(description = "Assigned developer")
    private UserDto assignee;
    @Schema(description = "User who created the task")
    private UserDto createdBy;
    @Schema(description = "ISO-8601 creation timestamp", example = "2025-10-01T09:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "ISO-8601 timestamp of the last status change or ping", example = "2025-10-05T14:30:00")
    private LocalDateTime lastActivityAt;
    @Schema(description = "ISO-8601 deadline timestamp", example = "2025-11-30T17:00:00")
    private LocalDateTime deadline;
    @Schema(description = "Number of times the assignee has pinged this task", example = "2")
    private int pingCount;
    @Schema(description = "IDs of tasks that directly depend on (are blocked by) this task", example = "[10, 11]")
    private List<Long> dependentTaskIds;
    @Schema(description = "IDs of tasks that this task depends on (upstream parents)", example = "[5, 6]")
    private List<Long> dependencyIds;
    @Schema(description = "Optimistic-locking version counter", example = "3")
    private Long version;

    // --- Dynamic Gravity Engine Calculation Metrics ---
    @Schema(description = "Composite Gravity Score used to rank the task into a canvas zone (FOCUS ≥ 80, DRIFTING ≥ 50, ORBIT < 50)",
            example = "87.4")
    private double totalGravityScore;
    
    // Formula Components Breakdown
    @Schema(description = "Dependency blast-radius score component (logarithmic)", example = "30.0")
    private double dependencyScore;
    @Schema(description = "Number of tasks directly blocked by this task", example = "3")
    private int directBlockedCount;
    @Schema(description = "Number of tasks transitively blocked by this task (BFS downstream count)", example = "7")
    private int indirectBlockedCount;

    @Schema(description = "Deadline-proximity score component (sigmoid)", example = "25.5")
    private double deadlineProximityScore;
    @Schema(description = "Hours remaining until the task deadline (negative if overdue)", example = "48")
    private long hoursUntilDeadline;
    @Schema(description = "True if the task is past its deadline", example = "false")
    private boolean overdue;

    @Schema(description = "Stagnation score component based on inactivity duration", example = "10.2")
    private double stagnationScore;
    @Schema(description = "Hours since the last task activity (status change or ping)", example = "72")
    private long hoursInactive;

    @Schema(description = "Workload-density score component based on assignee's current load", example = "8.0")
    private double workloadDensityScore;
    @Schema(description = "Number of active (non-DONE) tasks currently assigned to this user", example = "5")
    private long assigneeActiveTasksCount;

    @Schema(description = "Name of the custom manager factor applied (null if not applied)", nullable = true, example = "High Business Value")
    private String customFactorName;
    @Schema(description = "Bonus points contributed by the custom manager factor", example = "20.0")
    private double customFactorScore;

    @Schema(description = "Environment multiplier applied to the total score (PRODUCTION=1.5, STAGING=1.2, DEVELOPMENT=1.0)",
            example = "1.5")
    private double environmentMultiplier;
    @Schema(description = "Canvas zone derived from totalGravityScore: FOCUS (≥80), DRIFTING (≥50), ORBIT (<50)",
            example = "FOCUS")
    private String canvasZone; // "FOCUS", "DRIFTING", "ORBIT"


    public TaskResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public TaskEnvironment getEnvironment() { return environment; }
    public void setEnvironment(TaskEnvironment environment) { this.environment = environment; }

    public boolean isCustomFactorApplied() { return customFactorApplied; }
    public void setCustomFactorApplied(boolean customFactorApplied) { this.customFactorApplied = customFactorApplied; }

    public boolean isPinged() { return pinged; }
    public void setPinged(boolean pinged) { this.pinged = pinged; }

    public UserDto getAssignee() { return assignee; }
    public void setAssignee(UserDto assignee) { this.assignee = assignee; }

    public UserDto getCreatedBy() { return createdBy; }
    public void setCreatedBy(UserDto createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public int getPingCount() { return pingCount; }
    public void setPingCount(int pingCount) { this.pingCount = pingCount; }

    public List<Long> getDependentTaskIds() { return dependentTaskIds; }
    public void setDependentTaskIds(List<Long> dependentTaskIds) { this.dependentTaskIds = dependentTaskIds; }

    public List<Long> getDependencyIds() { return dependencyIds; }
    public void setDependencyIds(List<Long> dependencyIds) { this.dependencyIds = dependencyIds; }

    public double getTotalGravityScore() { return totalGravityScore; }
    public void setTotalGravityScore(double totalGravityScore) { this.totalGravityScore = totalGravityScore; }

    public double getDependencyScore() { return dependencyScore; }
    public void setDependencyScore(double dependencyScore) { this.dependencyScore = dependencyScore; }

    public int getDirectBlockedCount() { return directBlockedCount; }
    public void setDirectBlockedCount(int directBlockedCount) { this.directBlockedCount = directBlockedCount; }

    public int getIndirectBlockedCount() { return indirectBlockedCount; }
    public void setIndirectBlockedCount(int indirectBlockedCount) { this.indirectBlockedCount = indirectBlockedCount; }

    public double getDeadlineProximityScore() { return deadlineProximityScore; }
    public void setDeadlineProximityScore(double deadlineProximityScore) { this.deadlineProximityScore = deadlineProximityScore; }

    public long getHoursUntilDeadline() { return hoursUntilDeadline; }
    public void setHoursUntilDeadline(long hoursUntilDeadline) { this.hoursUntilDeadline = hoursUntilDeadline; }

    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }

    public double getStagnationScore() { return stagnationScore; }
    public void setStagnationScore(double stagnationScore) { this.stagnationScore = stagnationScore; }

    public long getHoursInactive() { return hoursInactive; }
    public void setHoursInactive(long hoursInactive) { this.hoursInactive = hoursInactive; }

    public double getWorkloadDensityScore() { return workloadDensityScore; }
    public void setWorkloadDensityScore(double workloadDensityScore) { this.workloadDensityScore = workloadDensityScore; }

    public long getAssigneeActiveTasksCount() { return assigneeActiveTasksCount; }
    public void setAssigneeActiveTasksCount(long assigneeActiveTasksCount) { this.assigneeActiveTasksCount = assigneeActiveTasksCount; }

    public String getCustomFactorName() { return customFactorName; }
    public void setCustomFactorName(String customFactorName) { this.customFactorName = customFactorName; }

    public double getCustomFactorScore() { return customFactorScore; }
    public void setCustomFactorScore(double customFactorScore) { this.customFactorScore = customFactorScore; }

    public double getEnvironmentMultiplier() { return environmentMultiplier; }
    public void setEnvironmentMultiplier(double environmentMultiplier) { this.environmentMultiplier = environmentMultiplier; }

    public String getCanvasZone() { return canvasZone; }
    public void setCanvasZone(String canvasZone) { this.canvasZone = canvasZone; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
