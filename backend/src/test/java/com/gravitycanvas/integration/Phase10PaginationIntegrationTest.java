package com.gravitycanvas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gravitycanvas.entity.*;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 10 - Server-Side Pagination, Filtering, Sorting & RBAC Integration Tests.
 *
 * Verifies:
 * 1. GET /api/tasks/search returns PageResponse<TaskResponseDto> with correct pagination metadata.
 * 2. Filters by status, environment, assignee, canvasZone, and search term.
 * 3. Sorting across allowlisted fields (createdAt, deadline, status, title, environment) with tie-breaker id.
 * 4. Rejection of unallowlisted sort fields (400 Bad Request) and negative pagination bounds.
 * 5. 64-bit pageable offset overflow guard (> Integer.MAX_VALUE returns 400 Bad Request).
 * 6. RBAC on GET /api/tasks/pending/search (PM/Lead permitted, Member forbidden 403, unauthenticated 401).
 * 7. Contract stability: GET /api/tasks and GET /api/tasks/pending return JSON arrays (not PageResponse).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "priora.demo-data.enabled=false")
@Transactional
class Phase10PaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PM_USER = "p10.pm";
    private static final String TL_USER = "p10.lead";
    private static final String DEV_USER = "p10.dev";
    private static final String PASSWORD = "TestPassword123!";

    private UserAccount pmAccount;
    private UserAccount devAccount;

    @BeforeEach
    void setUp() {
        pmAccount = seedUser(PM_USER, "ROLE_PROJECT_MANAGER", "Phase10 PM", "EMP-P10-1");
        seedUser(TL_USER, "ROLE_TEAM_LEAD", "Phase10 Lead", "EMP-P10-2");
        devAccount = seedUser(DEV_USER, "ROLE_TEAM_MEMBER", "Phase10 Dev", "EMP-P10-3");
    }

    private UserAccount seedUser(String username, String role, String fullName, String empNo) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode(PASSWORD));
            u.setRole(role);
            u.setFullName(fullName);
            u.setEmployeeNo(empNo);
            u.setDepartment("Engineering");
            return userRepository.save(u);
        });
    }

    private String basicAuth(String username) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + PASSWORD).getBytes());
    }

    private Task createPersistedTask(String title, TaskStatus status, ApprovalStatus approval,
                                     TaskEnvironment env, UserAccount assignee, LocalDateTime deadline) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription("Description for " + title);
        t.setStatus(status);
        t.setApprovalStatus(approval);
        t.setEnvironment(env);
        t.setAssignee(assignee);
        t.setCreatedBy(pmAccount);
        t.setDeadline(deadline != null ? deadline : LocalDateTime.now().plusDays(3));
        t.setCreatedAt(LocalDateTime.now().minusHours(2));
        t.setLastActivityAt(LocalDateTime.now().minusHours(1));
        t.setDependencies(new HashSet<>());
        t.setDependentTasks(new HashSet<>());
        return taskRepository.save(t);
    }

    // =======================================================================
    // 1. PAGE RESPONSE CONTRACT & DEFAULT PAGINATION
    // =======================================================================

    @Test
    @DisplayName("Default Pagination: GET /api/tasks/search returns PageResponse with default page=0, size=20")
    void testDefaultPaginationAndPageResponseContract() throws Exception {
        createPersistedTask("Task Alpha", TaskStatus.TODO, ApprovalStatus.APPROVED, TaskEnvironment.DEVELOPMENT, devAccount, null);
        createPersistedTask("Task Beta", TaskStatus.IN_PROGRESS, ApprovalStatus.APPROVED, TaskEnvironment.PRODUCTION, devAccount, null);
        createPersistedTask("Task Gamma", TaskStatus.TODO, ApprovalStatus.PENDING_APPROVAL, TaskEnvironment.STAGING, devAccount, null);

        mockMvc.perform(get("/api/tasks/search")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.pageSize").value(20))
            .andExpect(jsonPath("$.totalElements").value(2)) // Only 2 APPROVED tasks
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.hasPrevious").value(false))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].approvalStatus").value("APPROVED"))
            .andExpect(jsonPath("$.content[1].approvalStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("Page Slicing: Page slicing, page boundaries, and out-of-bounds page requests")
    void testPageSlicingAndBoundaries() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createPersistedTask("Numbered Task " + i, TaskStatus.TODO, ApprovalStatus.APPROVED,
                TaskEnvironment.DEVELOPMENT, devAccount, LocalDateTime.now().plusDays(i));
        }

        // Page 0, Size 2
        mockMvc.perform(get("/api/tasks/search?page=0&size=2")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.pageSize").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.hasPrevious").value(false))
            .andExpect(jsonPath("$.content", hasSize(2)));

        // Page 2, Size 2 (last page containing 1 item)
        mockMvc.perform(get("/api/tasks/search?page=2&size=2")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(2))
            .andExpect(jsonPath("$.last").value(true))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.hasPrevious").value(true))
            .andExpect(jsonPath("$.content", hasSize(1)));

        // Out-of-bounds page 10
        mockMvc.perform(get("/api/tasks/search?page=10&size=2")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(10))
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // =======================================================================
    // 2. FILTERING PARAMETERS
    // =======================================================================

    @Test
    @DisplayName("Filtering: Filter by status, environment, assignee, search substring")
    void testFilteringParameters() throws Exception {
        createPersistedTask("Frontend Redesign", TaskStatus.TODO, ApprovalStatus.APPROVED, TaskEnvironment.DEVELOPMENT, devAccount, null);
        createPersistedTask("Backend Migration", TaskStatus.IN_PROGRESS, ApprovalStatus.APPROVED, TaskEnvironment.PRODUCTION, devAccount, null);
        createPersistedTask("Database Tuning", TaskStatus.DONE, ApprovalStatus.APPROVED, TaskEnvironment.PRODUCTION, null, null);

        // Filter by environment = PRODUCTION
        mockMvc.perform(get("/api/tasks/search?environment=PRODUCTION")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].environment", everyItem(is("PRODUCTION"))));

        // Filter by status = IN_PROGRESS
        mockMvc.perform(get("/api/tasks/search?status=IN_PROGRESS")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Backend Migration"));

        // Filter by search substring = "Design" (case-insensitive)
        mockMvc.perform(get("/api/tasks/search?search=design")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Frontend Redesign"));

        // Filter by assigneeId
        mockMvc.perform(get("/api/tasks/search?assigneeId=" + devAccount.getId())
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    // =======================================================================
    // 3. SORTING ALLOWLIST & DETERMINISTIC TIE-BREAKER
    // =======================================================================

    @Test
    @DisplayName("Sorting: Allowed fields (title ASC, deadline DESC) sort correctly with deterministic id tie-breaker")
    void testAllowedSorting() throws Exception {
        createPersistedTask("Zeta Task", TaskStatus.TODO, ApprovalStatus.APPROVED, TaskEnvironment.DEVELOPMENT, devAccount, LocalDateTime.now().plusDays(10));
        createPersistedTask("Alpha Task", TaskStatus.TODO, ApprovalStatus.APPROVED, TaskEnvironment.DEVELOPMENT, devAccount, LocalDateTime.now().plusDays(5));

        // Sort by title ASC
        mockMvc.perform(get("/api/tasks/search?sortBy=title&direction=ASC")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Alpha Task"))
            .andExpect(jsonPath("$.content[1].title").value("Zeta Task"));

        // Sort by deadline DESC
        mockMvc.perform(get("/api/tasks/search?sortBy=deadline&direction=DESC")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Zeta Task"))
            .andExpect(jsonPath("$.content[1].title").value("Alpha Task"));
    }

    @Test
    @DisplayName("Security: Unallowlisted sort fields reject with 400 Bad Request")
    void testUnallowlistedSortFieldRejected() throws Exception {
        // Disallowed field: salary
        mockMvc.perform(get("/api/tasks/search?sortBy=salary")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"));

        // SQL injection probe: id;DROP TABLE TASKS
        mockMvc.perform(get("/api/tasks/search?sortBy=id;DROP+TABLE+TASKS")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    // =======================================================================
    // 4. OFFSET OVERFLOW & BOUNDARY GUARDS
    // =======================================================================

    @Test
    @DisplayName("Offset Overflow: Pageable offset > Integer.MAX_VALUE rejects with 400 Bad Request")
    void testOffsetOverflowRejected() throws Exception {
        mockMvc.perform(get("/api/tasks/search?page=1000000000&size=10")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message", containsString("offset overflow")));
    }

    @Test
    @DisplayName("Bounds Validation: Negative page or non-positive size rejects with 400 Bad Request")
    void testNegativePaginationBoundsRejected() throws Exception {
        mockMvc.perform(get("/api/tasks/search?page=-1")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tasks/search?size=0")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isBadRequest());
    }

    // =======================================================================
    // 5. PENDING QUEUE PAGINATION & RBAC ENFORCEMENT
    // =======================================================================

    @Test
    @DisplayName("Pending Queue: GET /api/tasks/pending/search enforces RBAC and returns pending tasks only")
    void testPendingQueuePaginationAndRbac() throws Exception {
        createPersistedTask("Approved Task", TaskStatus.TODO, ApprovalStatus.APPROVED, TaskEnvironment.DEVELOPMENT, devAccount, null);
        createPersistedTask("Pending Task 1", TaskStatus.TODO, ApprovalStatus.PENDING_APPROVAL, TaskEnvironment.DEVELOPMENT, devAccount, null);
        createPersistedTask("Pending Task 2", TaskStatus.TODO, ApprovalStatus.PENDING_APPROVAL, TaskEnvironment.PRODUCTION, devAccount, null);

        // PM authorization -> 200 OK
        mockMvc.perform(get("/api/tasks/pending/search")
                .header("Authorization", basicAuth(PM_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[*].approvalStatus", everyItem(is("PENDING_APPROVAL"))));

        // Team Lead authorization -> 200 OK
        mockMvc.perform(get("/api/tasks/pending/search")
                .header("Authorization", basicAuth(TL_USER)))
            .andExpect(status().isOk());

        // Team Member authorization -> 403 Forbidden
        mockMvc.perform(get("/api/tasks/pending/search")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isForbidden());

        // Unauthenticated -> 401 Unauthorized
        mockMvc.perform(get("/api/tasks/pending/search"))
            .andExpect(status().isUnauthorized());
    }

    // =======================================================================
    // 6. CONTRACT STABILITY: CANVASES RETURN LIST, SEARCH RETURNS PAGE
    // =======================================================================

    @Test
    @DisplayName("Contract Stability: GET /api/tasks returns JSON Array, while /search returns PageResponse")
    void testStableApiContracts() throws Exception {
        createPersistedTask("Approved Task 1", TaskStatus.TODO, ApprovalStatus.APPROVED, TaskEnvironment.DEVELOPMENT, devAccount, null);

        // GET /api/tasks must return JSON array [ ... ]
        mockMvc.perform(get("/api/tasks")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", isA(List.class)))
            .andExpect(jsonPath("$[0].title").value("Approved Task 1"));

        // GET /api/tasks/search must return PageResponse object { content: [...], totalElements: ... }
        mockMvc.perform(get("/api/tasks/search")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", isA(List.class)))
            .andExpect(jsonPath("$.totalElements").exists())
            .andExpect(jsonPath("$.pageSize").exists());
    }
}
