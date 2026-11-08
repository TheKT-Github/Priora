package com.gravitycanvas.integration;

import com.gravitycanvas.dto.PageResponse;
import com.gravitycanvas.dto.TaskFilterParams;
import com.gravitycanvas.dto.TaskResponseDto;
import com.gravitycanvas.entity.*;
import com.gravitycanvas.repository.GravitySettingsRepository;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import com.gravitycanvas.service.TaskService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10 - Query Count & Hibernate Statistics Performance Integration Tests.
 *
 * Verifies:
 * 1. Deep dependency chain A -> B -> C -> D -> E on Canvas:
 *    When all 5 tasks are approved Canvas tasks, findDirectDependencyEdges(approvedIds)
 *    retrieves all 4 edges in the initial edge query, resulting in W_transitive = 0 additional
 *    database queries. With 1 distinct assignee, total SQL count is exactly 4 queries.
 * 2. Transitive BFS database queries are triggered only when downstream nodes are outside
 *    the initial approved ID set and have not been fetched yet.
 * 3. Paginated Catalog query bounds: 1 ID page query + 1 count query + 1 single-valued hydration
 *    + 1 direct edge query + W_transitive BFS queries + U workload queries.
 * 4. Zero Cartesian collection joins and 0 secondary collection fetches.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class Phase10QueryCountIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private TaskService taskService;

    @Autowired
    private GravitySettingsRepository settingsRepository;

    private Statistics statistics;
    private UserAccount testUser;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        testUser = new UserAccount();
        testUser.setUsername("perf10_user_" + System.currentTimeMillis());
        testUser.setPassword("Password123!");
        testUser.setRole("ROLE_TEAM_MEMBER");
        testUser.setFullName("Performance Phase10 User");
        testUser.setEmployeeNo("EMP-P10");
        testUser.setDepartment("Engineering");
        testUser = userRepository.save(testUser);

        if (settingsRepository.count() == 0) {
            settingsRepository.save(new GravitySettings(null, true, true, true, true, true));
        }

        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    private Task createPersistedTask(String title, TaskStatus status, ApprovalStatus approval, UserAccount assignee) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription("Performance test task " + title);
        t.setStatus(status);
        t.setApprovalStatus(approval);
        t.setEnvironment(TaskEnvironment.PRODUCTION);
        t.setAssignee(assignee);
        t.setCreatedBy(assignee);
        t.setDeadline(LocalDateTime.now().plusDays(2));
        t.setCreatedAt(LocalDateTime.now().minusHours(4));
        t.setLastActivityAt(LocalDateTime.now().minusHours(1));
        t.setDependencies(new HashSet<>());
        t.setDependentTasks(new HashSet<>());
        return taskRepository.save(t);
    }

    // =======================================================================
    // 1. DEEP CHAIN CANVAS QUERY BOUNDS (W_transitive = 0)
    // =======================================================================

    @Test
    @DisplayName("Deep Chain Canvas: A -> B -> C -> D -> E all approved -> W_transitive=0, total queries <= 4")
    void testDeepChainCanvasQueryCount() {
        // Create 5 approved tasks forming chain A -> B -> C -> D -> E
        Task a = createPersistedTask("Task A", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);
        Task b = createPersistedTask("Task B", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);
        Task c = createPersistedTask("Task C", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);
        Task d = createPersistedTask("Task D", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);
        Task e = createPersistedTask("Task E", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);

        // A blocks B, B blocks C, C blocks D, D blocks E
        a.getDependentTasks().add(b);
        b.getDependencies().add(a);

        b.getDependentTasks().add(c);
        c.getDependencies().add(b);

        c.getDependentTasks().add(d);
        d.getDependencies().add(c);

        d.getDependentTasks().add(e);
        e.getDependencies().add(d);

        taskRepository.saveAll(List.of(a, b, c, d, e));

        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // Execute Canvas retrieval
        List<TaskResponseDto> results = taskService.getAllTasks();

        assertNotNull(results);
        assertEquals(5, results.size());

        // Expected queries:
        // 1. findAllApprovedWithSingleValued()
        // 2. findDirectDependencyEdges([a,b,c,d,e]) -> retrieves all 4 edges: (a,b), (b,c), (c,d), (d,e)
        //    All downstream nodes (b,c,d,e) were present in the initial approved list, so adjacency is known.
        //    W_transitive = 0 (no extra queries to database)
        // 3. settingsService.getSettings() (1 query)
        // 4. countByAssigneeIdAndStatusNot for testUser (1 query, cached for all 5 tasks)
        // Total expected = 4 queries!
        long totalQueries = statistics.getPrepareStatementCount();
        assertTrue(totalQueries <= 4,
            "Total queries (" + totalQueries + ") must be <= 4 when all chain nodes are in initial approved set");
        assertEquals(0, statistics.getCollectionFetchCount(),
            "Must have 0 secondary collection fetches");

        // Verify downstream counts for A: direct = 1 (B), indirect = 3 (C, D, E)
        TaskResponseDto aDto = results.stream().filter(t -> t.getId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals(1, aDto.getDirectBlockedCount());
        assertEquals(3, aDto.getIndirectBlockedCount());
    }

    // =======================================================================
    // 2. PAGINATED CATALOG QUERY BOUNDS (Stage 1 + Stage 2)
    // =======================================================================

    @Test
    @DisplayName("Catalog Query Bounds: searchTasks issues exactly bounded queries (ID page + count + hydrate + edges + workload)")
    void testPaginatedCatalogQueryBounds() {
        for (int i = 1; i <= 10; i++) {
            createPersistedTask("Catalog Task " + i, TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);
        }

        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        TaskFilterParams params = new TaskFilterParams();
        PageResponse<TaskResponseDto> page = taskService.searchTasks(params, 0, 5);

        assertNotNull(page);
        assertEquals(5, page.getContent().size());
        assertEquals(10, page.getTotalElements());
        assertEquals(2, page.getTotalPages());

        // In searchTasks():
        // 1 query for findTaskIds (Criteria with LIMIT 5)
        // 1 query for countTasks (matching predicates)
        // 1 query for findRootEntitiesByIdIn (hydrating 5 tasks with single-valued joins)
        // 1 query for findDirectDependencyEdges (for the 5 page IDs)
        // 0 W_transitive queries (no dependencies exist)
        // 1 query for settings
        // 1 query for testUser workload count
        // Total queries = 6
        long totalQueries = statistics.getPrepareStatementCount();
        assertTrue(totalQueries <= 6,
            "Paginated catalog search query count (" + totalQueries + ") must be bounded <= 6");
        assertEquals(0, statistics.getCollectionFetchCount(),
            "Must have 0 secondary collection fetches");
    }

    // =======================================================================
    // 3. DIAMOND GRAPH & CYCLE VISITED-SET PROTECTION
    // =======================================================================

    @Test
    @DisplayName("Cycle Protection: Cycle in Canvas tasks does not cause infinite loop or repeated queries")
    void testCycleInCanvasTasks() {
        Task t1 = createPersistedTask("Cycle 1", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);
        Task t2 = createPersistedTask("Cycle 2", TaskStatus.TODO, ApprovalStatus.APPROVED, testUser);

        t1.getDependentTasks().add(t2);
        t2.getDependencies().add(t1);

        t2.getDependentTasks().add(t1);
        t1.getDependencies().add(t2);

        taskRepository.saveAll(List.of(t1, t2));

        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        List<TaskResponseDto> results = taskService.getAllTasks();
        assertNotNull(results);
        assertEquals(2, results.size());

        long totalQueries = statistics.getPrepareStatementCount();
        assertTrue(totalQueries <= 4, "Total queries must be <= 4");
    }
}
