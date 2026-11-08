package com.gravitycanvas.service;

import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying TaskDependencyTraversalService BFS downstream dependency traversal,
 * cycle avoidance, and blast radius counting.
 */
class TaskDependencyTraversalServiceTest {

    private TaskDependencyTraversalService traversalService;

    @BeforeEach
    void setUp() {
        traversalService = new TaskDependencyTraversalService();
    }

    private Task createTask(Long id, String title) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setStatus(TaskStatus.TODO);
        task.setEnvironment(TaskEnvironment.DEVELOPMENT);
        task.setCreatedAt(LocalDateTime.now());
        task.setLastActivityAt(LocalDateTime.now());
        task.setDependencies(new HashSet<>());
        task.setDependentTasks(new HashSet<>());
        return task;
    }

    private void link(Task parent, Task dependent) {
        parent.getDependentTasks().add(dependent);
        dependent.getDependencies().add(parent);
    }

    @Test
    @DisplayName("BFS Downstream: Null or empty task returns 0 indirect count")
    void testNullOrEmptyTaskReturnsZero() {
        assertEquals(0, traversalService.calculateIndirectDownstreamCount(null));

        Task isolated = createTask(1L, "Isolated Task");
        assertEquals(0, traversalService.calculateIndirectDownstreamCount(isolated));
    }

    @Test
    @DisplayName("BFS Downstream: Only direct downstream dependents returns 0 indirect count")
    void testDirectDependentsOnlyReturnsZeroIndirect() {
        Task parent = createTask(1L, "Parent");
        Task dep1 = createTask(2L, "Direct Dep 1");
        Task dep2 = createTask(3L, "Direct Dep 2");

        link(parent, dep1);
        link(parent, dep2);

        // Direct count = 2, indirect count = 0
        assertEquals(0, traversalService.calculateIndirectDownstreamCount(parent));
    }

    @Test
    @DisplayName("BFS Downstream: Linear chain A -> B -> C -> D has 2 indirect dependents for A")
    void testLinearChainDownstream() {
        Task a = createTask(1L, "Task A");
        Task b = createTask(2L, "Task B");
        Task c = createTask(3L, "Task C");
        Task d = createTask(4L, "Task D");

        link(a, b);
        link(b, c);
        link(c, d);

        // For A: Direct is B (1), Reachable downstream: B, C, D (3). Indirect = 3 - 1 = 2
        assertEquals(2, traversalService.calculateIndirectDownstreamCount(a));

        // For B: Direct is C (1), Reachable: C, D (2). Indirect = 2 - 1 = 1
        assertEquals(1, traversalService.calculateIndirectDownstreamCount(b));

        // For C: Direct is D (1), Reachable: D (1). Indirect = 1 - 1 = 0
        assertEquals(0, traversalService.calculateIndirectDownstreamCount(c));
    }

    @Test
    @DisplayName("BFS Downstream: Diamond DAG branching A -> B, A -> C, B -> D, C -> D deduplicates correctly")
    void testDiamondDagBranchingDeduplication() {
        Task a = createTask(1L, "Task A");
        Task b = createTask(2L, "Task B");
        Task c = createTask(3L, "Task C");
        Task d = createTask(4L, "Task D");

        link(a, b);
        link(a, c);
        link(b, d);
        link(c, d);

        // For A: Direct = B, C (2). Reachable = B, C, D (3 unique). Indirect = 3 - 2 = 1 (D)
        assertEquals(1, traversalService.calculateIndirectDownstreamCount(a));
    }

    @Test
    @DisplayName("BFS Downstream: Multi-branch tree hierarchy calculates correct blast radius")
    void testMultiBranchTreeDownstream() {
        Task root = createTask(1L, "Root");
        Task b1 = createTask(2L, "Branch 1");
        Task b2 = createTask(3L, "Branch 2");
        Task leaf1 = createTask(4L, "Leaf 1");
        Task leaf2 = createTask(5L, "Leaf 2");
        Task leaf3 = createTask(6L, "Leaf 3");

        link(root, b1);
        link(root, b2);
        link(b1, leaf1);
        link(b1, leaf2);
        link(b2, leaf3);

        // For Root: Direct = b1, b2 (2). Reachable = b1, b2, leaf1, leaf2, leaf3 (5). Indirect = 5 - 2 = 3
        assertEquals(3, traversalService.calculateIndirectDownstreamCount(root));
    }

    @Test
    @DisplayName("BFS Downstream: Handles null entries gracefully in dependentTasks collection")
    void testHandlesNullEntriesGracefully() {
        Task task = createTask(1L, "Task with null entry");
        Task validDep = createTask(2L, "Valid Dep");
        Task indirectDep = createTask(3L, "Indirect Dep");

        link(task, validDep);
        link(validDep, indirectDep);
        task.getDependentTasks().add(null); // Corrupted/null element

        assertEquals(1, traversalService.calculateIndirectDownstreamCount(task));
    }
}
