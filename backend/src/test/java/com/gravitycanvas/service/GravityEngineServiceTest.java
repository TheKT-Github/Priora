package com.gravitycanvas.service;

import com.gravitycanvas.entity.GravitySettings;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Comprehensive JUnit 5 Unit Tests for GravityEngineService.
 * Validates exact two-phase deadline proximity calculation & capping rules:
 * 1. Task due in 24 hours -> ~50.0 pts.
 * 2. Task due in 0 hours -> 100.0 pts.
 * 3. Task overdue by 5 hours -> 105.0 pts.
 * 4. Task overdue by 100 hours -> 130.0 pts (capped at +30.0 max penalty).
 * 5. Logarithmic Blast Radius scaling.
 * 6. Stagnation pauses during IN_REVIEW.
 * 7. Instant +25 points boost for BLOCKED.
 * 8. Environment Multipliers (PROD 1.5x vs STAGING 1.2x vs DEV 1.0x).
 * 9. Status Override Rule (DONE status forces ORBIT zone).
 */
class GravityEngineServiceTest {

    private TaskRepository taskRepository;
    private GravityEngineService gravityEngineService;

    @BeforeEach
    void setUp() {
        taskRepository = Mockito.mock(TaskRepository.class);
        gravityEngineService = new GravityEngineService(taskRepository);

        when(taskRepository.countByAssigneeIdAndStatusNot(anyLong(), eq(TaskStatus.DONE))).thenReturn(0L);
    }

    @Test
    @DisplayName("Deadline Test 1: Task due in 24 hours -> expected deadline score ~50.0 pts")
    void testDeadlineScore24Hours() {
        double score = gravityEngineService.calculateDeadlineScore(24);
        assertEquals(50.0, score, 0.1, "Score for task due in 24 hours should be 50.0 pts");
    }

    @Test
    @DisplayName("Deadline Test 2: Task due in 0 hours -> expected deadline score 100.0 pts")
    void testDeadlineScore0Hours() {
        double score = gravityEngineService.calculateDeadlineScore(0);
        assertEquals(100.0, score, 0.1, "Score for task due in 0 hours should be 100.0 pts");
    }

    @Test
    @DisplayName("Deadline Test 3: Task overdue by 5 hours -> expected deadline score 105.0 pts")
    void testDeadlineScoreOverdue5Hours() {
        double score = gravityEngineService.calculateDeadlineScore(-5);
        assertEquals(105.0, score, 0.1, "Score for task overdue by 5 hours should be 105.0 pts");
    }

    @Test
    @DisplayName("Deadline Test 4: Task overdue by 100 hours -> expected deadline score 130.0 pts (capped)")
    void testDeadlineScoreOverdue100HoursCapped() {
        double score = gravityEngineService.calculateDeadlineScore(-100);
        assertEquals(130.0, score, 0.1, "Score for task overdue by 100 hours should be capped at 130.0 pts");
    }

    @Test
    @DisplayName("Test 5: Verify Logarithmic Blast Radius Scaling")
    void testLogarithmicBlastRadiusScaling() {
        Task directTask1 = new Task();
        directTask1.setId(2L);
        directTask1.setDependentTasks(new HashSet<>());

        Task indirectTask1 = new Task();
        indirectTask1.setId(3L);
        indirectTask1.setDependentTasks(new HashSet<>());
        directTask1.getDependentTasks().add(indirectTask1);

        Task mainTask = new Task();
        mainTask.setId(1L);
        mainTask.setStatus(TaskStatus.IN_PROGRESS);
        mainTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        mainTask.setDeadline(LocalDateTime.now().plusHours(24));
        mainTask.setLastActivityAt(LocalDateTime.now());

        Set<Task> dependents = new HashSet<>();
        dependents.add(directTask1);
        mainTask.setDependentTasks(dependents);

        double score = gravityEngineService.calculateGravityScore(mainTask);
        assertTrue(score > 50.0, "Score should include blast radius points on top of base urgency");
    }

    @Test
    @DisplayName("Test 6: Verify Stagnation Pauses During IN_REVIEW")
    void testStagnationPausesDuringInReview() {
        LocalDateTime daysAgo = LocalDateTime.now().minusDays(10);

        Task activeTask = new Task();
        activeTask.setStatus(TaskStatus.IN_PROGRESS);
        activeTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        activeTask.setDeadline(LocalDateTime.now().plusHours(24));
        activeTask.setLastActivityAt(daysAgo);

        Task reviewTask = new Task();
        reviewTask.setStatus(TaskStatus.IN_REVIEW);
        reviewTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        reviewTask.setDeadline(LocalDateTime.now().plusHours(24));
        reviewTask.setLastActivityAt(daysAgo);

        double scoreActive = gravityEngineService.calculateGravityScore(activeTask);
        double scoreReview = gravityEngineService.calculateGravityScore(reviewTask);

        assertTrue(scoreActive > scoreReview, "IN_PROGRESS score (" + scoreActive + ") should accumulate stagnation points over IN_REVIEW (" + scoreReview + ")");
    }

    @Test
    @DisplayName("Test 7: Verify Instant +25 Points Boost When Status Changes to BLOCKED")
    void testInstantBlockerPenaltyBoost() {
        LocalDateTime futureDeadline = LocalDateTime.now().plusHours(48);

        Task normalTask = new Task();
        normalTask.setStatus(TaskStatus.IN_PROGRESS);
        normalTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        normalTask.setDeadline(futureDeadline);
        normalTask.setLastActivityAt(LocalDateTime.now());

        Task blockedTask = new Task();
        blockedTask.setStatus(TaskStatus.BLOCKED);
        blockedTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        blockedTask.setDeadline(futureDeadline);
        blockedTask.setLastActivityAt(LocalDateTime.now());

        double normalScore = gravityEngineService.calculateGravityScore(normalTask);
        double blockedScore = gravityEngineService.calculateGravityScore(blockedTask);

        assertEquals(25.0, Math.round((blockedScore - normalScore) * 10.0) / 10.0, "BLOCKED status should add exactly +25.0 points boost");
    }

    @Test
    @DisplayName("Test 8: Verify Environment Multipliers (PROD 1.5x vs STAGING 1.2x vs DEV 1.0x)")
    void testEnvironmentMultipliers() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(24).plusMinutes(2);

        Task devTask = new Task();
        devTask.setStatus(TaskStatus.IN_PROGRESS);
        devTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        devTask.setDeadline(deadline);
        devTask.setLastActivityAt(LocalDateTime.now());

        Task stagingTask = new Task();
        stagingTask.setStatus(TaskStatus.IN_PROGRESS);
        stagingTask.setEnvironment(TaskEnvironment.STAGING);
        stagingTask.setDeadline(deadline);
        stagingTask.setLastActivityAt(LocalDateTime.now());

        Task prodTask = new Task();
        prodTask.setStatus(TaskStatus.IN_PROGRESS);
        prodTask.setEnvironment(TaskEnvironment.PRODUCTION);
        prodTask.setDeadline(deadline);
        prodTask.setLastActivityAt(LocalDateTime.now());

        double devScore = gravityEngineService.calculateGravityScore(devTask);
        double stagingScore = gravityEngineService.calculateGravityScore(stagingTask);
        double prodScore = gravityEngineService.calculateGravityScore(prodTask);

        assertEquals(devScore * 1.2, stagingScore, 1.0, "STAGING score should be 1.2x DEV score");
        assertEquals(devScore * 1.5, prodScore, 1.0, "PROD score should be 1.5x DEV score");
    }

    @Test
    @DisplayName("Test 9: Verify Status Override Rule (DONE Status Forces ORBIT Zone)")
    void testDoneStatusForcesOrbitZone() {
        Task doneTask = new Task();
        doneTask.setStatus(TaskStatus.DONE);
        doneTask.setEnvironment(TaskEnvironment.PRODUCTION);
        doneTask.setDeadline(LocalDateTime.now().minusHours(100));

        String zone = gravityEngineService.determineCanvasZone(doneTask, 150.0);
        assertEquals("ORBIT", zone, "Tasks with DONE status MUST evaluate as ORBIT regardless of raw score");
    }

    // ==========================================
    // PHASE 4 ENHANCEMENT TESTS: SETTINGS & ZONES
    // ==========================================

    @Test
    @DisplayName("Test 10: Disabled Dependency Score -> Blast Radius excluded (0.0 pts)")
    void testDisabledDependencyScore() {
        Task directTask = new Task();
        directTask.setId(2L);

        Task mainTask = new Task();
        mainTask.setId(1L);
        mainTask.setStatus(TaskStatus.IN_PROGRESS);
        mainTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        mainTask.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        mainTask.setLastActivityAt(LocalDateTime.now());
        mainTask.setDependentTasks(Set.of(directTask));

        // When enabled
        GravitySettings enabledSettings = new GravitySettings();
        enabledSettings.setEnableDependencyScore(true);
        double scoreEnabled = gravityEngineService.calculateGravityScore(mainTask, enabledSettings);

        // When disabled
        GravitySettings disabledSettings = new GravitySettings();
        disabledSettings.setEnableDependencyScore(false);
        double scoreDisabled = gravityEngineService.calculateGravityScore(mainTask, disabledSettings);

        assertTrue(scoreEnabled > scoreDisabled, "Score with dependency enabled (" + scoreEnabled + ") must exceed disabled (" + scoreDisabled + ")");
        assertEquals(50.0, scoreDisabled, 0.5, "When dependency is disabled, 24h task should only have deadline urgency (~50.0 pts)");
    }

    @Test
    @DisplayName("Test 11: Disabled Deadline Proximity -> Deadline Urgency excluded (0.0 pts)")
    void testDisabledDeadlineProximity() {
        Task mainTask = new Task();
        mainTask.setId(1L);
        mainTask.setStatus(TaskStatus.IN_PROGRESS);
        mainTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        mainTask.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        mainTask.setLastActivityAt(LocalDateTime.now());

        GravitySettings disabledSettings = new GravitySettings();
        disabledSettings.setEnableDeadlineProximityScore(false);

        double score = gravityEngineService.calculateGravityScore(mainTask, disabledSettings);
        assertEquals(0.0, score, 0.1, "When deadline score is disabled and no other factors active, score should be 0.0");
    }

    @Test
    @DisplayName("Test 12: Disabled Stagnation Score -> Stagnation excluded (0.0 pts)")
    void testDisabledStagnationScore() {
        Task oldTask = new Task();
        oldTask.setId(1L);
        oldTask.setStatus(TaskStatus.IN_PROGRESS);
        oldTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        oldTask.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        oldTask.setLastActivityAt(LocalDateTime.now().minusDays(14));

        GravitySettings enabledSettings = new GravitySettings();
        enabledSettings.setEnableStagnationScore(true);
        double scoreEnabled = gravityEngineService.calculateGravityScore(oldTask, enabledSettings);

        GravitySettings disabledSettings = new GravitySettings();
        disabledSettings.setEnableStagnationScore(false);
        double scoreDisabled = gravityEngineService.calculateGravityScore(oldTask, disabledSettings);

        assertTrue(scoreEnabled > scoreDisabled, "Enabled stagnation should produce higher score than disabled");
        assertEquals(50.0, scoreDisabled, 0.5, "Disabled stagnation should only leave 24h deadline urgency (~50.0 pts)");
    }

    @Test
    @DisplayName("Test 13: Disabled Workload Density Score -> Workload Multiplier defaults to 1.0 (neutral)")
    void testDisabledWorkloadDensityScore() {
        com.gravitycanvas.entity.UserAccount assignee = new com.gravitycanvas.entity.UserAccount();
        assignee.setId(99L);

        when(taskRepository.countByAssigneeIdAndStatusNot(eq(99L), eq(TaskStatus.DONE))).thenReturn(10L);

        Task busyTask = new Task();
        busyTask.setId(1L);
        busyTask.setStatus(TaskStatus.IN_PROGRESS);
        busyTask.setAssignee(assignee);
        busyTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        busyTask.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        busyTask.setLastActivityAt(LocalDateTime.now());

        // With workload enabled (10 tasks - 3 = 7 bottleneck * 0.05 = +0.35 -> 1.35x multiplier)
        GravitySettings enabledSettings = new GravitySettings();
        enabledSettings.setEnableWorkloadDensityScore(true);
        double scoreEnabled = gravityEngineService.calculateGravityScore(busyTask, enabledSettings);

        // With workload disabled -> multiplier must be 1.0 (50.0 * 1.0 = 50.0 pts)
        GravitySettings disabledSettings = new GravitySettings();
        disabledSettings.setEnableWorkloadDensityScore(false);
        double scoreDisabled = gravityEngineService.calculateGravityScore(busyTask, disabledSettings);

        assertEquals(50.0, scoreDisabled, 0.5, "When workload density is disabled, multiplier is 1.0 -> 50.0 pts");
        assertTrue(scoreEnabled > scoreDisabled, "Workload enabled should scale base score by 1.35x");
    }

    @Test
    @DisplayName("Test 14: Custom Factor - Added when enabled AND applied to task")
    void testCustomFactorAppliedWhenEnabled() {
        Task task = new Task();
        task.setId(1L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2)); // exactly 24 hours remaining -> 50.0 pts
        task.setLastActivityAt(LocalDateTime.now());
        task.setCustomFactorApplied(true);

        GravitySettings settings = new GravitySettings();
        settings.setEnableCustomFactor(true);
        settings.setCustomFactorName("Security Audit Weight");
        settings.setCustomFactorPoints(15.0);

        double score = gravityEngineService.calculateGravityScore(task, settings);
        assertEquals(65.0, score, 0.5, "Custom factor (+15.0) should be added to base score (50.0 + 15.0 = 65.0)");
    }

    @Test
    @DisplayName("Test 15: Custom Factor - NOT added when setting is disabled even if applied on task")
    void testCustomFactorIgnoredWhenSettingDisabled() {
        Task task = new Task();
        task.setId(1L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        task.setLastActivityAt(LocalDateTime.now());
        task.setCustomFactorApplied(true);

        GravitySettings settings = new GravitySettings();
        settings.setEnableCustomFactor(false);
        settings.setCustomFactorPoints(15.0);

        double score = gravityEngineService.calculateGravityScore(task, settings);
        assertEquals(50.0, score, 0.5, "When enableCustomFactor is false, custom factor must contribute 0.0 pts");
    }

    @Test
    @DisplayName("Test 16: Custom Factor - NOT added when setting is enabled but task did NOT apply it")
    void testCustomFactorIgnoredWhenTaskNotMarked() {
        Task task = new Task();
        task.setId(1L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        task.setLastActivityAt(LocalDateTime.now());
        task.setCustomFactorApplied(false);

        GravitySettings settings = new GravitySettings();
        settings.setEnableCustomFactor(true);
        settings.setCustomFactorPoints(20.0);

        double score = gravityEngineService.calculateGravityScore(task, settings);
        assertEquals(50.0, score, 0.5, "When task customFactorApplied is false, custom factor must contribute 0.0 pts");
    }

    @Test
    @DisplayName("Test 17: Multi-factor Combination - All toggles disabled leaves only blocker penalty")
    void testAllFactorsDisabledLeavesOnlyBlockerPenalty() {
        Task blockedTask = new Task();
        blockedTask.setId(1L);
        blockedTask.setStatus(TaskStatus.BLOCKED);
        blockedTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        blockedTask.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        blockedTask.setLastActivityAt(LocalDateTime.now().minusDays(5));

        GravitySettings allDisabled = new GravitySettings(null, false, false, false, false, false);

        double score = gravityEngineService.calculateGravityScore(blockedTask, allDisabled);
        assertEquals(25.0, score, 0.1, "All factors disabled should leave exactly the 25.0 blocker penalty");
    }

    @Test
    @DisplayName("Test 18: Null settings safely defaults to all standard factors enabled")
    void testNullSettingsDefaultsSafely() {
        Task task = new Task();
        task.setId(1L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setDeadline(LocalDateTime.now().plusHours(24).plusMinutes(2));
        task.setLastActivityAt(LocalDateTime.now());

        double scoreNull = gravityEngineService.calculateGravityScore(task, null);
        double scoreDefault = gravityEngineService.calculateGravityScore(task);

        assertEquals(scoreDefault, scoreNull, 0.01, "Passing null settings must produce identical score to default overload");
        assertEquals(50.0, scoreNull, 0.5, "Null settings must include 24h deadline urgency (~50.0 pts)");
    }

    @Test
    @DisplayName("Test 19: Priority Zone Boundaries (49.9, 50.0, 79.9, 80.0)")
    void testPriorityZoneBoundaries() {
        Task task = new Task();
        task.setStatus(TaskStatus.IN_PROGRESS);

        assertEquals("ORBIT", gravityEngineService.determineCanvasZone(task, 49.9), "Score 49.9 must be ORBIT");
        assertEquals("DRIFTING", gravityEngineService.determineCanvasZone(task, 50.0), "Score 50.0 must be DRIFTING");
        assertEquals("DRIFTING", gravityEngineService.determineCanvasZone(task, 79.9), "Score 79.9 must be DRIFTING");
        assertEquals("FOCUS", gravityEngineService.determineCanvasZone(task, 80.0), "Score 80.0 must be FOCUS");
        assertEquals("FOCUS", gravityEngineService.determineCanvasZone(task, 120.0), "Score 120.0 must be FOCUS");
    }

    @Test
    @DisplayName("Test 20: Priority Zone DONE Override on all boundary values")
    void testDoneOverrideOnAllBoundaries() {
        Task doneTask = new Task();
        doneTask.setStatus(TaskStatus.DONE);

        assertEquals("ORBIT", gravityEngineService.determineCanvasZone(doneTask, 80.0), "DONE task at 80.0 must be ORBIT");
        assertEquals("ORBIT", gravityEngineService.determineCanvasZone(doneTask, 79.9), "DONE task at 79.9 must be ORBIT");
        assertEquals("ORBIT", gravityEngineService.determineCanvasZone(doneTask, 50.0), "DONE task at 50.0 must be ORBIT");
        assertEquals("ORBIT", gravityEngineService.determineCanvasZone(doneTask, 49.9), "DONE task at 49.9 must be ORBIT");
    }
}
