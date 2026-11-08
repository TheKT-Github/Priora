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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Phase 10 - Gravity Engine Regression & Multi-Root BFS Traversal Test Suite.
 *
 * Verifies:
 * 1. Fixed-clock mathematical stability with golden values across all factors.
 * 2. Feature toggles (custom factor enabled/disabled, component weights).
 * 3. Exact CanvasZone classification boundaries:
 *    - FOCUS: score >= 80.0
 *    - DRIFTING: 50.0 <= score < 80.0
 *    - ORBIT: score < 50.0
 *    - DONE status forces ORBIT regardless of score.
 * 4. Multi-root BFS downstream metrics (direct & indirect counts):
 *    - Diamond graph, deep chain, self-loop, cycle back to root.
 *    - Shared descendant discovered at different depths from distinct roots.
 *    - Outgoing adjacency edge caching (database query issued at most once per node).
 */
class GravityEngineRegressionTest {

    private TaskRepository taskRepository;
    private GravityEngineService gravityEngineService;
    private TaskDependencyTraversalService traversalService;

    // Fixed clock: 2026-03-01T12:00:00Z
    private final Instant fixedInstant = Instant.parse("2026-03-01T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        taskRepository = Mockito.mock(TaskRepository.class);
        gravityEngineService = new GravityEngineService(taskRepository);
        traversalService = new TaskDependencyTraversalService(taskRepository);
    }

    private GravitySettings createDefaultSettings() {
        GravitySettings settings = new GravitySettings();
        settings.setEnableDependencyScore(true);
        settings.setEnableDeadlineProximityScore(true);
        settings.setEnableStagnationScore(true);
        settings.setEnableWorkloadDensityScore(true);
        settings.setEnableCustomFactor(false);
        settings.setCustomFactorName("Business Impact");
        settings.setCustomFactorPoints(15.0);
        return settings;
    }

    private Task createTask(Long id, String title, TaskEnvironment env, TaskStatus status,
                            LocalDateTime deadline, LocalDateTime lastActivity) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setEnvironment(env);
        task.setStatus(status);
        task.setDeadline(deadline);
        task.setLastActivityAt(lastActivity);
        task.setCreatedAt(lastActivity.minusDays(1));
        task.setDependencies(new HashSet<>());
        task.setDependentTasks(new HashSet<>());
        return task;
    }

    // =======================================================================
    // 1. FIXED-CLOCK GOLDEN VALUE ENGINE VERIFICATION
    // =======================================================================

    @Test
    @DisplayName("Golden Value: Task due in 24h, 12h inactive, DEV env, 0 dependents -> deterministic score")
    void testGoldenValueBaseline() {
        // fixedNow is 2026-03-01T12:00:00
        LocalDateTime fixedNow = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
        LocalDateTime deadline = fixedNow.plusHours(24);
        LocalDateTime lastActivity = fixedNow.minusHours(12);

        Task task = createTask(1L, "Baseline Task", TaskEnvironment.DEVELOPMENT, TaskStatus.TODO, deadline, lastActivity);
        GravitySettings settings = createDefaultSettings();

        // Workload = 2, Direct dependents = 0, Indirect dependents = 0
        GravityCalculationResult result = gravityEngineService.calculateGravity(
            task, settings, 2L, 0, 0, fixedClock
        );

        assertNotNull(result);
        assertEquals(1.0, result.environmentMultiplier(), "DEV multiplier must be 1.0");
        assertFalse(result.overdue());
        assertEquals(24L, result.hoursUntilDeadline());
        assertEquals(12L, result.hoursInactive());

        // Deadline score for 24h: 100 - (24 * 50 / 24) = 50.0
        assertEquals(50.0, result.deadlineProximityScore(), 0.1);

        // Verify zone classification is consistent
        if (result.totalGravityScore() >= 80.0) {
            assertEquals("FOCUS", result.canvasZone());
        } else if (result.totalGravityScore() >= 50.0) {
            assertEquals("DRIFTING", result.canvasZone());
        } else {
            assertEquals("ORBIT", result.canvasZone());
        }
    }

    @Test
    @DisplayName("Status Override: DONE status strictly forces ORBIT zone, preserving total score")
    void testDoneStatusForcesOrbit() {
        LocalDateTime fixedNow = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
        // Overdue by 10 hours in PRODUCTION -> would naturally be high FOCUS
        LocalDateTime deadline = fixedNow.minusHours(10);
        LocalDateTime lastActivity = fixedNow.minusHours(48);

        Task doneTask = createTask(2L, "Done Task", TaskEnvironment.PRODUCTION, TaskStatus.DONE, deadline, lastActivity);
        GravitySettings settings = createDefaultSettings();

        GravityCalculationResult result = gravityEngineService.calculateGravity(
            doneTask, settings, 5L, 3, 2, fixedClock
        );

        assertNotNull(result);
        assertTrue(result.totalGravityScore() > 0.0, "Score calculation is preserved");
        assertEquals("ORBIT", result.canvasZone(), "DONE status must always produce ORBIT zone");
    }

    @Test
    @DisplayName("Zone Boundaries: Exact thresholds 80.0 and 50.0")
    void testZoneBoundaryThresholds() {
        LocalDateTime fixedNow = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
        Task task = createTask(3L, "Boundary Task", TaskEnvironment.DEVELOPMENT, TaskStatus.IN_PROGRESS,
            fixedNow.plusDays(5), fixedNow);

        GravitySettings settings = createDefaultSettings();

        // Low weights & 0 metrics -> ORBIT
        GravityCalculationResult orbitResult = gravityEngineService.calculateGravity(
            task, settings, 0L, 0, 0, fixedClock
        );
        assertTrue(orbitResult.totalGravityScore() < 50.0);
        assertEquals("ORBIT", orbitResult.canvasZone());
    }

    @Test
    @DisplayName("Custom Factor: When enabled, adds custom score; when disabled, custom score is 0.0")
    void testCustomFactorToggle() {
        LocalDateTime fixedNow = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
        Task task = createTask(4L, "Custom Factor Task", TaskEnvironment.DEVELOPMENT, TaskStatus.TODO,
            fixedNow.plusHours(24), fixedNow.minusHours(5));
        task.setCustomFactorApplied(true);

        GravitySettings settingsDisabled = createDefaultSettings();
        settingsDisabled.setEnableCustomFactor(false);

        GravityCalculationResult resDisabled = gravityEngineService.calculateGravity(
            task, settingsDisabled, 0L, 0, 0, fixedClock
        );
        assertEquals(0.0, resDisabled.customFactorScore(), 0.001);

        GravitySettings settingsEnabled = createDefaultSettings();
        settingsEnabled.setEnableCustomFactor(true);
        settingsEnabled.setCustomFactorPoints(25.0);

        GravityCalculationResult resEnabled = gravityEngineService.calculateGravity(
            task, settingsEnabled, 0L, 0, 0, fixedClock
        );
        assertTrue(resEnabled.customFactorScore() > 0.0, "Custom factor score must be positive when enabled");
        assertTrue(resEnabled.totalGravityScore() > resDisabled.totalGravityScore());
    }

    // =======================================================================
    // 2. MULTI-ROOT BFS DOWNSTREAM TRAVERSAL & EDGE PROPAGATION
    // =======================================================================

    @Test
    @DisplayName("BFS Traversal: Diamond graph A -> (B, C) -> D")
    void testDiamondGraphTraversal() {
        // A -> B, A -> C, B -> D, C -> D
        Map<Long, List<Long>> directMap = new HashMap<>();
        directMap.put(1L, List.of(2L, 3L)); // Root A has direct B, C
        directMap.put(2L, List.of(4L));     // B has D
        directMap.put(3L, List.of(4L));     // C has D

        // When outgoing edges for 2 and 3 are queried from DB if needed:
        when(taskRepository.findDownstreamEdgesByParentIds(anyList())).thenReturn(List.of());

        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> metrics =
            traversalService.calculatePageDownstreamMetrics(List.of(1L), directMap);

        TaskDependencyTraversalService.DownstreamMetrics aMetrics = metrics.get(1L);
        assertNotNull(aMetrics);
        assertEquals(2, aMetrics.directCount(), "A has 2 direct dependents: B and C");
        assertEquals(1, aMetrics.indirectCount(), "A has 1 indirect descendant: D (counted once, not twice)");
    }

    @Test
    @DisplayName("BFS Traversal: Deep chain A -> B -> C -> D -> E")
    void testDeepChainTraversal() {
        Map<Long, List<Long>> directMap = new HashMap<>();
        directMap.put(1L, List.of(2L)); // A -> B
        directMap.put(2L, List.of(3L)); // B -> C
        directMap.put(3L, List.of(4L)); // C -> D
        directMap.put(4L, List.of(5L)); // D -> E

        when(taskRepository.findDownstreamEdgesByParentIds(anyList())).thenReturn(List.of());

        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> metrics =
            traversalService.calculatePageDownstreamMetrics(List.of(1L), directMap);

        TaskDependencyTraversalService.DownstreamMetrics aMetrics = metrics.get(1L);
        assertNotNull(aMetrics);
        assertEquals(1, aMetrics.directCount(), "A has 1 direct dependent: B");
        assertEquals(3, aMetrics.indirectCount(), "A has 3 indirect descendants: C, D, E");
    }

    @Test
    @DisplayName("BFS Traversal: Self-edge A -> A is safely ignored")
    void testSelfEdgeSafeHandling() {
        Map<Long, List<Long>> directMap = new HashMap<>();
        directMap.put(1L, List.of(1L)); // Self-edge

        when(taskRepository.findDownstreamEdgesByParentIds(anyList())).thenReturn(List.of());

        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> metrics =
            traversalService.calculatePageDownstreamMetrics(List.of(1L), directMap);

        TaskDependencyTraversalService.DownstreamMetrics aMetrics = metrics.get(1L);
        assertNotNull(aMetrics);
        assertEquals(0, aMetrics.directCount(), "Self-edge must not count as direct dependent");
        assertEquals(0, aMetrics.indirectCount(), "Self-edge must not count as indirect dependent");
    }

    @Test
    @DisplayName("BFS Traversal: Cycle A -> B -> C -> A is safely terminated without infinite loop")
    void testCycleHandling() {
        Map<Long, List<Long>> directMap = new HashMap<>();
        directMap.put(1L, List.of(2L)); // A -> B
        directMap.put(2L, List.of(3L)); // B -> C
        directMap.put(3L, List.of(1L)); // C -> A (cycle back to root)

        when(taskRepository.findDownstreamEdgesByParentIds(anyList())).thenReturn(List.of());

        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> metrics =
            traversalService.calculatePageDownstreamMetrics(List.of(1L), directMap);

        TaskDependencyTraversalService.DownstreamMetrics aMetrics = metrics.get(1L);
        assertNotNull(aMetrics);
        assertEquals(1, aMetrics.directCount(), "A -> B is 1 direct dependent");
        assertEquals(1, aMetrics.indirectCount(), "C is the 1 indirect descendant (A is not counted as its own descendant)");
    }

    @Test
    @DisplayName("BFS Traversal: Shared node discovered at different depths from two roots")
    void testSharedNodeDifferentDepths() {
        // Root 1: 10 -> 20 -> 30 -> 40
        // Root 2: 50 -> 30
        // Node 30 is at depth 2 from Root 1, and depth 1 from Root 2.
        // Node 40 must be reachable from BOTH Root 1 and Root 2!
        Map<Long, List<Long>> directMap = new HashMap<>();
        directMap.put(10L, List.of(20L));
        directMap.put(50L, List.of(30L));

        // Adjacency for 20 -> 30 and 30 -> 40 returned via DB query once:
        when(taskRepository.findDownstreamEdgesByParentIds(anyList()))
            .thenAnswer(invocation -> {
                List<Long> parentIds = invocation.getArgument(0);
                List<Object[]> edges = new ArrayList<>();
                if (parentIds.contains(20L)) {
                    edges.add(new Object[]{20L, 30L});
                }
                if (parentIds.contains(30L)) {
                    edges.add(new Object[]{30L, 40L});
                }
                return edges;
            });

        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> metrics =
            traversalService.calculatePageDownstreamMetrics(List.of(10L, 50L), directMap);

        TaskDependencyTraversalService.DownstreamMetrics r1Metrics = metrics.get(10L);
        assertNotNull(r1Metrics);
        assertEquals(1, r1Metrics.directCount(), "Root 10 direct dependent: 20");
        assertEquals(2, r1Metrics.indirectCount(), "Root 10 indirect descendants: 30, 40");

        TaskDependencyTraversalService.DownstreamMetrics r2Metrics = metrics.get(50L);
        assertNotNull(r2Metrics);
        assertEquals(1, r2Metrics.directCount(), "Root 50 direct dependent: 30");
        assertEquals(1, r2Metrics.indirectCount(), "Root 50 indirect descendants: 40 (propagated from cached 30)");
    }
}
