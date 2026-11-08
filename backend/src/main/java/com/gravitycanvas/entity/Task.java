package com.gravitycanvas.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Task entity holding attributes required for the Gravity Score formula calculation,
 * ping toggle alerts, custom factor application, and task lifecycle tracking.
 */
@Entity
@Table(
    name = "TASKS",
    indexes = {
        // Composite index covering countByAssigneeIdAndStatusNot(assigneeId, DONE).
        // A composite (ASSIGNEE_ID, STATUS) index outperforms two separate indexes for this AND predicate
        // because the database navigates the B-tree with ASSIGNEE_ID as the leading column and filters
        // STATUS within those rows in a single index scan. Two separate indexes would require a bitmap-and
        // merge or a full-scan on the lower-selectivity column.
        // APPROVAL_STATUS index is intentionally omitted: filtering on approvalStatus is currently performed
        // in-memory after findAll(), so a WHERE-clause index provides no benefit at this time.
        @Index(name = "idx_tasks_assignee_status", columnList = "ASSIGNEE_ID, STATUS")
    }
)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "APPROVAL_STATUS", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;

    @Column(name = "REJECTION_REASON", length = 1000)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskEnvironment environment = TaskEnvironment.DEVELOPMENT;

    @Column(name = "CUSTOM_FACTOR_APPLIED", nullable = false)
    private boolean customFactorApplied = false;

    @Column(name = "PINGED", nullable = false)
    private boolean pinged = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ASSIGNEE_ID")
    private UserAccount assignee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CREATED_BY_ID")
    private UserAccount createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "LAST_ACTIVITY_AT", nullable = false)
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    @Column(name = "DEADLINE", nullable = false)
    private LocalDateTime deadline;

    @Column(name = "PING_COUNT", nullable = false)
    private int pingCount = 0;

    // IMPORTANT: Both ManyToMany collections MUST remain Set<Task>, not List<Task>.
    // Changing to List would cause Hibernate's MultipleBagFetchException when both
    // collections are join-fetched simultaneously in findAllWithDependencies().
    // FetchType.LAZY — collection SQL is deferred until first access within an open session.
    // For list operations, findAllWithDependencies() pre-loads both collections via EntityGraph.
    // For single-task writes, BFS lazy-loads only the downstream subgraph of the target task.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "TASK_DEPENDENCIES",
        joinColumns = @JoinColumn(name = "PARENT_TASK_ID"),
        inverseJoinColumns = @JoinColumn(name = "DEPENDENT_TASK_ID"),
        // The composite PK (PARENT_TASK_ID, DEPENDENT_TASK_ID) already covers queries filtering
        // on PARENT_TASK_ID (loading dependentTasks). This index covers the inverse direction:
        // loading task.dependencies fires WHERE DEPENDENT_TASK_ID = ?, which the PK does not cover
        // as the leading column. PostgreSQL does NOT auto-create an index on FK columns.
        indexes = { @Index(name = "idx_task_deps_dependent_id", columnList = "DEPENDENT_TASK_ID") }
    )
    private Set<Task> dependentTasks = new HashSet<>();

    @ManyToMany(mappedBy = "dependentTasks", fetch = FetchType.LAZY)
    private Set<Task> dependencies = new HashSet<>();

    @Version
    @Column(name = "VERSION")
    private Long version;

    public Task() {}

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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

    public UserAccount getAssignee() { return assignee; }
    public void setAssignee(UserAccount assignee) { this.assignee = assignee; }

    public UserAccount getCreatedBy() { return createdBy; }
    public void setCreatedBy(UserAccount createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public int getPingCount() { return pingCount; }
    public void setPingCount(int pingCount) { this.pingCount = pingCount; }

    public Set<Task> getDependentTasks() { return dependentTasks; }
    public void setDependentTasks(Set<Task> dependentTasks) { this.dependentTasks = dependentTasks; }

    public Set<Task> getDependencies() { return dependencies; }
    public void setDependencies(Set<Task> dependencies) { this.dependencies = dependencies; }
}
