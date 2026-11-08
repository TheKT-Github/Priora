package com.gravitycanvas.service;

import com.gravitycanvas.entity.GravitySettings;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Core Architectural Mechanics & Mathematical Specification Service.
 * Implements two-phase deadline proximity calculation:
 * - Phase 1: Approaching Deadline (Smooth Sigmoid S-Curve up to 100.0 pts)
 * - Phase 2: Overdue Escalation (Base 100.0 pts + 1.0 pt per hour overdue, capped at +30.0 pts max)
 * 
 * Total Gravity Score = [ Base Score ] * Workload Multiplier * Environment Multiplier
 * Where Base Score = Blast Radius + Deadline Urgency + Stagnation + Blocker Penalty + Custom Factor
 * 
 * Configurable factor settings:
 * - enableDependencyScore: controls Blast Radius contribution (0.0 if disabled)
 * - enableDeadlineProximityScore: controls Deadline Urgency contribution (0.0 if disabled)
 * - enableStagnationScore: controls Stagnation contribution (0.0 if disabled)
 * - enableWorkloadDensityScore: controls Workload Multiplier (1.0 neutral if disabled)
 * - enableCustomFactor: incorporates customFactorPoints if enabled and task.isCustomFactorApplied()
 * Blocker Penalty (+25.0) and Environment Multipliers (DEV 1.0x, STAGING 1.2x, PROD 1.5x) are architectural invariants.
 */
@Service
public class GravityEngineService {

    private final TaskRepository taskRepository;
    private final TaskDependencyTraversalService traversalService;

    public GravityEngineService(TaskRepository taskRepository) {
        this(taskRepository, new TaskDependencyTraversalService());
    }

    @Autowired
    public GravityEngineService(TaskRepository taskRepository, TaskDependencyTraversalService traversalService) {
        this.taskRepository = taskRepository;
        this.traversalService = traversalService != null ? traversalService : new TaskDependencyTraversalService();
    }

    /**
     * Calculates the Deadline Proximity Score using two-phase curve & escalation capping.
     * 
     * @param hoursRemaining Hours until task deadline (negative if overdue)
     * @return Calculated Deadline Proximity Score (range 0.0 to 130.0 pts max)
     */
    public double calculateDeadlineScore(long hoursRemaining) {
        // Phase 1: Approaching Deadline (Smooth Sigmoid S-Curve up to 100.0 pts)
        if (hoursRemaining > 0) {
            return 100.0 / (1.0 + Math.exp(0.1 * (hoursRemaining - 24.0)));
        }

        // Phase 2: Overdue Escalation (Base 100.0 pts + 1.0 pt per hour overdue, capped at +30.0 pts max)
        // Total Deadline Proximity Score ranges from 100.0 to 130.0 pts max.
        long hoursOverdue = Math.abs(hoursRemaining);
        double overdueEscalation = Math.min(30.0, hoursOverdue * 1.0);

        return 100.0 + overdueEscalation;
    }

    /**
     * Master Gravity Engine calculation implementation.
     * Evaluates all scoring components, multipliers, total gravity score, and canvas zone.
     * Returns an immutable GravityCalculationResult record.
     */
    public GravityCalculationResult calculateGravity(
        Task task,
        GravitySettings settings,
        long activeAssigneeTasks,
        int directCount,
        int indirectCount,
        Clock clock
    ) {
        Clock effectiveClock = clock != null ? clock : Clock.systemDefaultZone();
        Instant nowInstant = effectiveClock.instant();
        ZoneId zoneId = effectiveClock.getZone();
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, zoneId);

        boolean depEnabled = (settings == null) || settings.isEnableDependencyScore();
        boolean deadlineEnabled = (settings == null) || settings.isEnableDeadlineProximityScore();
        boolean stagEnabled = (settings == null) || settings.isEnableStagnationScore();
        boolean workloadEnabled = (settings == null) || settings.isEnableWorkloadDensityScore();
        boolean customEnabled = (settings != null) && settings.isEnableCustomFactor();

        // 1. Blast Radius (Logarithmic Downstream Impact)
        double blastRadius = 0.0;
        if (depEnabled) {
            blastRadius = (directCount * 15.0) + (5.0 * (Math.log(1 + indirectCount) / Math.log(2.0)));
        }
        double roundedDependencyScore = depEnabled ? (Math.round(blastRadius * 10.0) / 10.0) : 0.0;

        // 2. Deadline Urgency (Two-Phase Sigmoid & Escalation Capped at 130.0 pts)
        long hoursRemaining = 0;
        boolean isOverdue = false;
        double deadlineUrgency = 0.0;
        if (task.getDeadline() != null) {
            hoursRemaining = Duration.between(now, task.getDeadline()).toHours();
            isOverdue = now.isAfter(task.getDeadline());
        }
        if (deadlineEnabled) {
            deadlineUrgency = calculateDeadlineScore(hoursRemaining);
        }
        double roundedDeadlineScore = deadlineEnabled ? (Math.round(deadlineUrgency * 10.0) / 10.0) : 0.0;

        // 3. Stagnation / Inactivity (Day-Based Aging)
        double stagnation = 0.0;
        long hoursInactive = 0;
        LocalDateTime lastActivity = task.getLastActivityAt() != null ? task.getLastActivityAt() : task.getCreatedAt();
        if (lastActivity != null) {
            hoursInactive = Math.max(0, Duration.between(lastActivity, now).toHours());
            if (stagEnabled && task.getStatus() != TaskStatus.IN_REVIEW && task.getStatus() != TaskStatus.DONE) {
                double daysInactive = Math.max(0.0, Duration.between(lastActivity, now).toMinutes() / (60.0 * 24.0));
                stagnation = Math.min(15.0, (daysInactive / 7.0) * 5.0);
            }
        }
        double roundedStagnationScore = stagEnabled ? (Math.round(stagnation * 10.0) / 10.0) : 0.0;

        // 4. Instant Blocker Penalty
        double blockerPenalty = (task.getStatus() == TaskStatus.BLOCKED) ? 25.0 : 0.0;

        // 5. Custom Factor Points (if enabled and applied to task)
        double customFactorScore = 0.0;
        String customFactorName = null;
        if (customEnabled) {
            customFactorName = settings.getCustomFactorName();
            if (task.isCustomFactorApplied() && settings.getCustomFactorPoints() != null) {
                customFactorScore = settings.getCustomFactorPoints();
            }
        }

        // 6. Workload Multiplier (Assignee Bottleneck) & Density Score Component
        double workloadMultiplier = 1.0;
        if (workloadEnabled) {
            workloadMultiplier = 1.0 + (0.05 * Math.max(0, activeAssigneeTasks - 3));
        }
        double roundedWorkloadScore = workloadEnabled ? (Math.round(activeAssigneeTasks * 2.0 * 10.0) / 10.0) : 0.0;

        // 7. Environment Multiplier
        double environmentMultiplier = (task.getEnvironment() != null) 
            ? task.getEnvironment().getMultiplier() 
            : 1.0;

        // SUM BASE SCORE
        double baseScore = blastRadius + deadlineUrgency + stagnation + blockerPenalty + customFactorScore;

        // FINAL TOTAL GRAVITY SCORE
        double totalGravityScore = Math.round(baseScore * workloadMultiplier * environmentMultiplier * 10.0) / 10.0;

        // CANVAS ZONE (with DONE status override)
        String canvasZone = determineCanvasZone(task, totalGravityScore);

        return new GravityCalculationResult(
            roundedDependencyScore,
            directCount,
            indirectCount,
            roundedDeadlineScore,
            hoursRemaining,
            isOverdue,
            roundedStagnationScore,
            hoursInactive,
            roundedWorkloadScore,
            activeAssigneeTasks,
            blockerPenalty,
            customFactorName,
            customFactorScore,
            workloadMultiplier,
            environmentMultiplier,
            totalGravityScore,
            canvasZone
        );
    }

    public GravityCalculationResult calculateGravity(
        Task task,
        GravitySettings settings,
        long activeAssigneeTasks,
        int directCount,
        int indirectCount
    ) {
        return calculateGravity(task, settings, activeAssigneeTasks, directCount, indirectCount, Clock.systemDefaultZone());
    }

    /**
     * Calculates the Total Gravity Score for a task using default settings (all standard factors enabled).
     * 
     * @param task Target task entity
     * @return Calculated Total Gravity Score
     */
    public double calculateGravityScore(Task task) {
        return calculateGravityScore(task, null);
    }

    /**
     * Calculates the Total Gravity Score for a given task according to exact mathematical specs
     * and dynamic manager settings toggles. Executes workload count query if assignee is present.
     * 
     * @param task Target task entity
     * @param settings Manager-customized scoring settings (if null, defaults to all standard factors enabled)
     * @return Calculated Total Gravity Score
     */
    public double calculateGravityScore(Task task, GravitySettings settings) {
        long activeAssigneeTasks = 0;
        boolean workloadEnabled = (settings == null) || settings.isEnableWorkloadDensityScore();
        if (workloadEnabled && task.getAssignee() != null && taskRepository != null) {
            activeAssigneeTasks = taskRepository.countByAssigneeIdAndStatusNot(
                task.getAssignee().getId(), TaskStatus.DONE
            );
        }
        return calculateGravityScore(task, settings, activeAssigneeTasks);
    }

    /**
     * Optimized overload for calculating Total Gravity Score reusing a pre-calculated active workload count.
     * Delegates to master calculateGravity implementation.
     * 
     * @param task Target task entity
     * @param settings Manager-customized scoring settings (if null, defaults to all standard factors enabled)
     * @param activeAssigneeTasks Pre-calculated count of active tasks assigned to the task's assignee
     * @return Calculated Total Gravity Score
     */
    public double calculateGravityScore(Task task, GravitySettings settings, long activeAssigneeTasks) {
        int directCount = task.getDependentTasks() != null ? task.getDependentTasks().size() : 0;
        int indirectCount = calculateIndirectDownstreamCount(task);
        return calculateGravity(task, settings, activeAssigneeTasks, directCount, indirectCount, Clock.systemDefaultZone())
            .totalGravityScore();
    }

    /**
     * Determines Canvas Zone ("FOCUS", "DRIFTING", "ORBIT") based on Gravity Score and Status Override Rule.
     * Thresholds:
     * - FOCUS: score >= 80.0
     * - DRIFTING: score >= 50.0 and score < 80.0
     * - ORBIT: score < 50.0 (or status == DONE)
     */
    public String determineCanvasZone(Task task, double gravityScore) {
        if (task != null && task.getStatus() == TaskStatus.DONE) {
            return "ORBIT";
        }
        if (gravityScore >= 80.0) {
            return "FOCUS";
        }
        if (gravityScore >= 50.0) {
            return "DRIFTING";
        }
        return "ORBIT";
    }

    /**
     * Calculates the number of indirect downstream tasks dependent on the start task using BFS.
     * Delegates to TaskDependencyTraversalService to eliminate algorithmic duplication.
     */
    public int calculateIndirectDownstreamCount(Task startTask) {
        return traversalService.calculateIndirectDownstreamCount(startTask);
    }
}
