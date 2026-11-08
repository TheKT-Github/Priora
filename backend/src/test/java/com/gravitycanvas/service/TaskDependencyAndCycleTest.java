package com.gravitycanvas.service;

import com.gravitycanvas.dto.TaskResponseDto;
import com.gravitycanvas.dto.UpdateTaskDependenciesRequest;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.exception.CircularDependencyException;
import com.gravitycanvas.exception.ResourceNotFoundException;
import com.gravitycanvas.repository.GravitySettingsRepository;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit test suite validating DAG dependency management, cycle detection,
 * and state preservation in TaskService.
 */
class TaskDependencyAndCycleTest {

    private TaskRepository taskRepository;
    private UserAccountRepository userRepository;
    private GravitySettingsRepository settingsRepository;
    private GravityEngineService gravityEngineService;
    private GravityCalculatorService gravityCalculatorService;
    private SettingsService settingsService;
    private TaskService taskService;

    private Map<Long, Task> taskDatabase;

    @BeforeEach
    void setUp() {
        taskDatabase = new HashMap<>();

        taskRepository = Mockito.mock(TaskRepository.class);
        userRepository = Mockito.mock(UserAccountRepository.class);
        settingsRepository = Mockito.mock(GravitySettingsRepository.class);

        when(taskRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.ofNullable(taskDatabase.get(id));
        });

        when(taskRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(taskDatabase.values()));

        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            taskDatabase.put(t.getId(), t);
            return t;
        });

        when(settingsRepository.findAll()).thenReturn(List.of());

        gravityEngineService = new GravityEngineService(taskRepository);
        settingsService = new SettingsService(settingsRepository);
        gravityCalculatorService = new GravityCalculatorService(gravityEngineService, taskRepository, settingsService);
        taskService = new TaskService(taskRepository, userRepository, gravityCalculatorService, settingsService);

        // Preload standard tasks A, B, C, D
        createAndRegisterTask(1L, "Task A");
        createAndRegisterTask(2L, "Task B");
        createAndRegisterTask(3L, "Task C");
        createAndRegisterTask(4L, "Task D");
    }

    private Task createAndRegisterTask(Long id, String title) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setStatus(TaskStatus.TODO);
        t.setEnvironment(TaskEnvironment.DEVELOPMENT);
        t.setDeadline(LocalDateTime.now().plusHours(48));
        t.setCreatedAt(LocalDateTime.now());
        t.setLastActivityAt(LocalDateTime.now());
        t.setDependencies(new HashSet<>());
        t.setDependentTasks(new HashSet<>());
        taskDatabase.put(id, t);
        return t;
    }

    @Test
    @DisplayName("DAG Test 1: Valid dependency chain A -> B -> C is accepted")
    void testValidDependencyChainAccepted() {
        // Task B depends on Task A (A blocks B, A -> B)
        TaskResponseDto dtoB = taskService.addDependency(2L, 1L);
        assertNotNull(dtoB);

        // Task C depends on Task B (B blocks C, B -> C)
        TaskResponseDto dtoC = taskService.addDependency(3L, 2L);
        assertNotNull(dtoC);

        Task a = taskDatabase.get(1L);
        Task b = taskDatabase.get(2L);
        Task c = taskDatabase.get(3L);

        assertTrue(a.getDependentTasks().contains(b), "A must block B (downstream)");
        assertTrue(b.getDependencies().contains(a), "B must depend on A (parent)");
        assertTrue(b.getDependentTasks().contains(c), "B must block C (downstream)");
        assertTrue(c.getDependencies().contains(b), "C must depend on B (parent)");
    }

    @Test
    @DisplayName("DAG Test 2: Self-dependency A -> A is rejected")
    void testSelfDependencyRejected() {
        CircularDependencyException ex = assertThrows(
            CircularDependencyException.class,
            () -> taskService.addDependency(1L, 1L)
        );
        assertTrue(ex.getMessage().contains("Self-dependency detected"));
    }

    @Test
    @DisplayName("DAG Test 3: Direct cycle A -> B -> A is rejected")
    void testDirectCycleRejected() {
        // A -> B
        taskService.addDependency(2L, 1L);

        // Attempt B -> A (making A depend on B)
        CircularDependencyException ex = assertThrows(
            CircularDependencyException.class,
            () -> taskService.addDependency(1L, 2L)
        );
        assertTrue(ex.getMessage().contains("Circular dependency detected"));
    }

    @Test
    @DisplayName("DAG Test 4: Indirect cycle A -> B -> C, then attempt C -> A is rejected")
    void testIndirectCycleRejected() {
        // A -> B -> C
        taskService.addDependency(2L, 1L);
        taskService.addDependency(3L, 2L);

        // Attempt C -> A (making A depend on C)
        CircularDependencyException ex = assertThrows(
            CircularDependencyException.class,
            () -> taskService.addDependency(1L, 3L)
        );
        assertTrue(ex.getMessage().contains("Circular dependency detected"));
    }

    @Test
    @DisplayName("DAG Test 5: Adding valid independent dependency D -> A is accepted")
    void testIndependentDependencyAccepted() {
        // A -> B -> C
        taskService.addDependency(2L, 1L);
        taskService.addDependency(3L, 2L);

        // D is independent, make A depend on D (D -> A -> B -> C)
        TaskResponseDto dtoA = taskService.addDependency(1L, 4L);
        assertNotNull(dtoA);

        Task d = taskDatabase.get(4L);
        Task a = taskDatabase.get(1L);
        assertTrue(d.getDependentTasks().contains(a));
        assertTrue(a.getDependencies().contains(d));
    }

    @Test
    @DisplayName("DAG Test 6: Attempting to create a cycle while updating dependencies is rejected")
    void testUpdateDependenciesCycleRejected() {
        // A -> B -> C
        taskService.addDependency(2L, 1L);
        taskService.addDependency(3L, 2L);

        // Attempt to update A's parent dependencies to include C (would create cycle)
        UpdateTaskDependenciesRequest req = new UpdateTaskDependenciesRequest(List.of(3L));
        CircularDependencyException ex = assertThrows(
            CircularDependencyException.class,
            () -> taskService.updateTaskDependencies(1L, req)
        );
        assertTrue(ex.getMessage().contains("Circular dependency detected"));
    }

    @Test
    @DisplayName("DAG Test 7: Updating dependencies with self-dependency is rejected")
    void testUpdateDependenciesSelfDependencyRejected() {
        UpdateTaskDependenciesRequest req = new UpdateTaskDependenciesRequest(List.of(1L));
        CircularDependencyException ex = assertThrows(
            CircularDependencyException.class,
            () -> taskService.updateTaskDependencies(1L, req)
        );
        assertTrue(ex.getMessage().contains("Self-dependency detected"));
    }

    @Test
    @DisplayName("DAG Test 8: Nonexistent parent task ID throws ResourceNotFoundException")
    void testNonexistentParentTaskIdThrowsNotFound() {
        UpdateTaskDependenciesRequest req = new UpdateTaskDependenciesRequest(List.of(9999L));
        ResourceNotFoundException ex = assertThrows(
            ResourceNotFoundException.class,
            () -> taskService.updateTaskDependencies(1L, req)
        );
        assertTrue(ex.getMessage().contains("Parent task not found with ID: 9999"));
    }

    @Test
    @DisplayName("DAG Test 9: Nonexistent target task ID throws ResourceNotFoundException")
    void testNonexistentTargetTaskIdThrowsNotFound() {
        UpdateTaskDependenciesRequest req = new UpdateTaskDependenciesRequest(List.of(1L));
        ResourceNotFoundException ex = assertThrows(
            ResourceNotFoundException.class,
            () -> taskService.updateTaskDependencies(8888L, req)
        );
        assertTrue(ex.getMessage().contains("Task not found with ID: 8888"));
    }

    @Test
    @DisplayName("DAG Test 10: Failed dependency change preserves database state unchanged")
    void testFailedDependencyChangePreservesState() {
        // A -> B
        taskService.addDependency(2L, 1L);

        Task aBefore = taskDatabase.get(1L);
        int aDepsBefore = aBefore.getDependencies().size();
        int aDownstreamBefore = aBefore.getDependentTasks().size();

        // Attempt invalid self-dependency on A
        assertThrows(CircularDependencyException.class, () -> taskService.addDependency(1L, 1L));

        Task aAfter = taskDatabase.get(1L);
        assertEquals(aDepsBefore, aAfter.getDependencies().size(), "A's dependencies count must remain unchanged");
        assertEquals(aDownstreamBefore, aAfter.getDependentTasks().size(), "A's dependent tasks count must remain unchanged");

        // Attempt invalid cycle on A (A depending on B)
        assertThrows(CircularDependencyException.class, () -> taskService.addDependency(1L, 2L));

        assertEquals(aDepsBefore, aAfter.getDependencies().size(), "A's dependencies must remain unchanged after rejected cycle");
    }

    @Test
    @DisplayName("DAG Test 11: Replacing dependencies successfully updates parent links")
    void testSuccessfulDependencyReplacement() {
        // Initially B depends on A
        taskService.addDependency(2L, 1L);

        Task a = taskDatabase.get(1L);
        Task b = taskDatabase.get(2L);
        Task d = taskDatabase.get(4L);

        assertTrue(b.getDependencies().contains(a));
        assertTrue(a.getDependentTasks().contains(b));

        // Now replace B's parent dependencies with D
        UpdateTaskDependenciesRequest req = new UpdateTaskDependenciesRequest(List.of(4L));
        TaskResponseDto dto = taskService.updateTaskDependencies(2L, req);
        assertNotNull(dto);

        assertFalse(b.getDependencies().contains(a), "B must no longer depend on A");
        assertFalse(a.getDependentTasks().contains(b), "A must no longer have B as dependent");
        assertTrue(b.getDependencies().contains(d), "B must now depend on D");
        assertTrue(d.getDependentTasks().contains(b), "D must now have B as dependent");
    }
}
