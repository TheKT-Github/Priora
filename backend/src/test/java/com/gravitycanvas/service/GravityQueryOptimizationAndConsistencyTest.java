package com.gravitycanvas.service;

import com.gravitycanvas.dto.TaskResponseDto;
import com.gravitycanvas.entity.GravitySettings;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.entity.UserAccount;
import com.gravitycanvas.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying that duplicate workload queries are eliminated during Gravity calculations,
 * and that scoring formulas and zones remain mathematically consistent.
 */
class GravityQueryOptimizationAndConsistencyTest {

    private TaskRepository taskRepository;
    private TaskDependencyTraversalService traversalService;
    private GravityEngineService gravityEngineService;
    private GravityCalculatorService gravityCalculatorService;

    @BeforeEach
    void setUp() {
        taskRepository = Mockito.mock(TaskRepository.class);
        traversalService = new TaskDependencyTraversalService();
        gravityEngineService = new GravityEngineService(taskRepository, traversalService);
        gravityCalculatorService = new GravityCalculatorService(
            gravityEngineService, taskRepository, null, traversalService
        );
    }

    private Task createTaskWithAssignee(Long taskId, Long assigneeId) {
        UserAccount assignee = new UserAccount();
        assignee.setId(assigneeId);
        assignee.setUsername("dev_user");
        assignee.setRole("ROLE_TEAM_MEMBER");

        Task task = new Task();
        task.setId(taskId);
        task.setTitle("Query Optimization Task");
        task.setStatus(TaskStatus.TODO);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setAssignee(assignee);
        task.setDeadline(LocalDateTime.now().plusHours(48));
        task.setCreatedAt(LocalDateTime.now().minusHours(2));
        task.setLastActivityAt(LocalDateTime.now().minusHours(1));
        task.setDependencies(new HashSet<>());
        task.setDependentTasks(new HashSet<>());
        return task;
    }

    @Test
    @DisplayName("Query Optimization: countByAssigneeIdAndStatusNot is executed EXACTLY ONCE per task")
    void testWorkloadQueryExecutedExactlyOnce() {
        Task task = createTaskWithAssignee(100L, 50L);

        // Assignee has 5 active tasks (which applies a workload bottleneck multiplier: 1.0 + 0.05 * (5 - 3) = 1.10)
        when(taskRepository.countByAssigneeIdAndStatusNot(eq(50L), eq(TaskStatus.DONE)))
            .thenReturn(5L);

        TaskResponseDto dto = gravityCalculatorService.calculateGravity(task, List.of(task));

        assertNotNull(dto);
        assertEquals(5L, dto.getAssigneeActiveTasksCount());

        // CRITICAL VERIFICATION: The query must be executed EXACTLY 1 time, demonstrating
        // that the redundant query in GravityEngineService was eliminated.
        verify(taskRepository, times(1)).countByAssigneeIdAndStatusNot(eq(50L), eq(TaskStatus.DONE));
    }

    @Test
    @DisplayName("Mathematical Consistency: Pre-calculated and standard calculateGravityScore produce identical scores")
    void testMathematicalScoringConsistency() {
        Task task = createTaskWithAssignee(200L, 60L);
        when(taskRepository.countByAssigneeIdAndStatusNot(eq(60L), eq(TaskStatus.DONE)))
            .thenReturn(6L);

        GravitySettings settings = new GravitySettings();
        settings.setEnableDependencyScore(true);
        settings.setEnableDeadlineProximityScore(true);
        settings.setEnableStagnationScore(true);
        settings.setEnableWorkloadDensityScore(true);

        // Standard calculation (fetches query internally)
        double scoreStandard = gravityEngineService.calculateGravityScore(task, settings);

        // Optimized overload with pre-calculated workload count (reuses value, 0 queries)
        double scoreOptimized = gravityEngineService.calculateGravityScore(task, settings, 6L);

        assertEquals(scoreStandard, scoreOptimized, 0.0001,
            "Optimized calculateGravityScore must return the exact same mathematical value as standard calculation");
    }

    @Test
    @DisplayName("Zero Assignee: When task has no assignee, 0 workload queries are executed")
    void testZeroQueriesWhenNoAssignee() {
        Task task = new Task();
        task.setId(300L);
        task.setTitle("Unassigned Task");
        task.setStatus(TaskStatus.TODO);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setAssignee(null);
        task.setDependencies(new HashSet<>());
        task.setDependentTasks(new HashSet<>());

        TaskResponseDto dto = gravityCalculatorService.calculateGravity(task, List.of(task));

        assertNotNull(dto);
        assertEquals(0L, dto.getAssigneeActiveTasksCount());

        // Zero queries should be executed
        verify(taskRepository, never()).countByAssigneeIdAndStatusNot(any(), any());
    }
}
