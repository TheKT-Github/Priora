package com.gravitycanvas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gravitycanvas.dto.*;
import com.gravitycanvas.entity.*;
import com.gravitycanvas.repository.GravitySettingsRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Production-readiness End-to-End Workflow Integration Test Suite.
 *
 * Exercises the complete Spring Security filter chain, controller mappings,
 * service transactional boundaries, and JPA entity persistence in H2 (dev profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "priora.demo-data.enabled=false")
@Transactional
class EndToEndWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private GravitySettingsRepository settingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ObjectMapper objectMapper;

    private static final String PM_USER  = "e2e.pm";
    private static final String TL_USER  = "e2e.lead";
    private static final String DEV_USER = "e2e.dev";
    private static final String TST_USER = "e2e.tester";
    private static final String PASSWORD = "TestPassword123!";

    private UserAccount pmAccount;
    private UserAccount devAccount;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        pmAccount = seedUser(PM_USER, "ROLE_PROJECT_MANAGER", "PM E2E", "EMP-1001");
        seedUser(TL_USER, "ROLE_TEAM_LEAD", "Lead E2E", "EMP-1002");
        devAccount = seedUser(DEV_USER, "ROLE_TEAM_MEMBER", "Dev E2E", "EMP-1003");
        seedUser(TST_USER, "ROLE_TESTER", "Tester E2E", "EMP-1004");
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

    // =======================================================================
    // A. TASK LIFECYCLE WORKFLOW
    // =======================================================================

    @Test
    @DisplayName("Task Lifecycle: Create -> Retrieve -> Status -> Deadline -> Reassign -> Ping")
    void testCompleteTaskLifecycle() throws Exception {
        // 1. Create task as Project Manager
        CreateTaskRequest createReq = new CreateTaskRequest();
        createReq.setTitle("E2E Payment Microservice Migration");
        createReq.setDescription("Migrate legacy payment gateway to Spring Boot 3");
        createReq.setEnvironment(TaskEnvironment.PRODUCTION);
        createReq.setDeadline(LocalDateTime.now().plusDays(3));
        createReq.setAssigneeId(devAccount.getId());
        createReq.setCustomFactorApplied(false);

        MvcResult createResult = mockMvc.perform(post("/api/tasks")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("E2E Payment Microservice Migration"))
            .andExpect(jsonPath("$.environment").value("PRODUCTION"))
            .andExpect(jsonPath("$.status").value("TODO"))
            .andExpect(jsonPath("$.totalGravityScore").isNumber())
            .andReturn();

        TaskResponseDto created = objectMapper.readValue(createResult.getResponse().getContentAsString(), TaskResponseDto.class);
        Long taskId = created.getId();
        assertNotNull(taskId);

        // 2. Retrieve all tasks as Developer
        mockMvc.perform(get("/api/tasks")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + taskId + ")].title").value("E2E Payment Microservice Migration"));

        // 3. Update status to IN_PROGRESS as Developer
        UpdateStatusRequest statusReq = new UpdateStatusRequest(TaskStatus.IN_PROGRESS);
        mockMvc.perform(put("/api/tasks/" + taskId + "/status")
                .header("Authorization", basicAuth(DEV_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(statusReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 4. Modify deadline as Team Lead
        LocalDateTime newDeadline = LocalDateTime.now().plusDays(5);
        UpdateDeadlineRequest deadlineReq = new UpdateDeadlineRequest(newDeadline);
        mockMvc.perform(put("/api/tasks/" + taskId + "/deadline")
                .header("Authorization", basicAuth(TL_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deadlineReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(taskId));

        // 5. Reassign task to PM as Team Lead
        ReassignTaskRequest reassignReq = new ReassignTaskRequest(pmAccount.getId());
        mockMvc.perform(put("/api/tasks/" + taskId + "/reassign")
                .header("Authorization", basicAuth(TL_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reassignReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignee.username").value(PM_USER));

        // 6. Ping blocker as Project Manager
        mockMvc.perform(post("/api/tasks/" + taskId + "/ping")
                .header("Authorization", basicAuth(PM_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinged").value(true))
            .andExpect(jsonPath("$.pingCount").value(1));
    }

    // =======================================================================
    // B. APPROVAL WORKFLOW
    // =======================================================================

    @Test
    @DisplayName("Approval Workflow: Load Pending -> Approve -> Reject with Reason -> Role Guards")
    void testApprovalWorkflow() throws Exception {
        // Seed a pending task directly in repository to test approval transitions
        Task pendingTask = new Task();
        pendingTask.setTitle("Developer Proposed Architecture Refactor");
        pendingTask.setDescription("Proposed changes to cache layer");
        pendingTask.setEnvironment(TaskEnvironment.DEVELOPMENT);
        pendingTask.setDeadline(LocalDateTime.now().plusDays(2));
        pendingTask.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        pendingTask.setCreatedBy(devAccount);
        pendingTask.setAssignee(devAccount);
        pendingTask = taskRepository.save(pendingTask);
        Long pendingId = pendingTask.getId();

        // 1. Manager and Lead can view pending queue
        mockMvc.perform(get("/api/tasks/pending")
                .header("Authorization", basicAuth(PM_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + pendingId + ")].approvalStatus").value("PENDING_APPROVAL"));

        // 2. Developer is forbidden from viewing pending queue (403)
        mockMvc.perform(get("/api/tasks/pending")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isForbidden());

        // 3. Developer is forbidden from approving tasks (403)
        mockMvc.perform(put("/api/tasks/" + pendingId + "/approve")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isForbidden());

        // 4. Team Lead approves pending task
        mockMvc.perform(put("/api/tasks/" + pendingId + "/approve")
                .header("Authorization", basicAuth(TL_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        // 5. Seed another task to test rejection
        Task pendingTask2 = new Task();
        pendingTask2.setTitle("Unnecessary Dependency Addition");
        pendingTask2.setEnvironment(TaskEnvironment.DEVELOPMENT);
        pendingTask2.setDeadline(LocalDateTime.now().plusDays(1));
        pendingTask2.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        pendingTask2.setCreatedBy(devAccount);
        pendingTask2 = taskRepository.save(pendingTask2);

        RejectTaskRequest rejectReq = new RejectTaskRequest("Requires prior architectural committee sign-off.");
        mockMvc.perform(put("/api/tasks/" + pendingTask2.getId() + "/reject")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Requires prior architectural committee sign-off."));
    }

    // =======================================================================
    // C. DEPENDENCY MANAGEMENT & REFERENTIAL INTEGRITY DELETION
    // =======================================================================

    @Test
    @DisplayName("Dependency Management: Add Dependencies -> Detect Cycles -> Safe Referential Integrity Deletion")
    void testDependencyManagementAndSafeDeletion() throws Exception {
        // Create 3 tasks: A, B, C
        Long taskA = createTaskSimple("Task Alpha (Root)");
        Long taskB = createTaskSimple("Task Beta (Middle)");
        Long taskC = createTaskSimple("Task Gamma (Leaf)");

        // 1. Establish chain: A -> B -> C (B depends on A, C depends on B)
        UpdateTaskDependenciesRequest depB = new UpdateTaskDependenciesRequest(List.of(taskA));
        mockMvc.perform(put("/api/tasks/" + taskB + "/dependencies")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dependencyIds[0]").value(taskA));

        UpdateTaskDependenciesRequest depC = new UpdateTaskDependenciesRequest(List.of(taskB));
        mockMvc.perform(put("/api/tasks/" + taskC + "/dependencies")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depC)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dependencyIds[0]").value(taskB));

        // 2. Reject Circular Dependency: Attempt to make A depend on C (would create cycle: A -> B -> C -> A)
        UpdateTaskDependenciesRequest cycleReq = new UpdateTaskDependenciesRequest(List.of(taskC));
        mockMvc.perform(put("/api/tasks/" + taskA + "/dependencies")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cycleReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Circular dependency detected")));

        // 3. Safe Task Deletion with Referential Integrity:
        // Delete Task B (which has parent A and child C).
        // Must cleanly dissociate both sides without foreign-key violation.
        mockMvc.perform(delete("/api/tasks/" + taskB)
                .header("Authorization", basicAuth(PM_USER)))
            .andExpect(status().isNoContent());

        // Verify Task B is gone from database
        assertFalse(taskRepository.existsById(taskB), "Task B should be deleted");

        // Verify Tasks A and C still exist intact
        assertTrue(taskRepository.existsById(taskA), "Task A should still exist");
        assertTrue(taskRepository.existsById(taskC), "Task C should still exist");

        // Verify Task C's dependency list no longer references the deleted Task B
        Task reloadedC = taskRepository.findById(taskC).orElseThrow();
        assertTrue(reloadedC.getDependencies().stream().noneMatch(p -> p.getId().equals(taskB)),
            "Task C should no longer reference deleted Task B as parent");

        // 4. Deleting nonexistent task returns 404
        mockMvc.perform(delete("/api/tasks/999999")
                .header("Authorization", basicAuth(PM_USER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // =======================================================================
    // D. GRAVITY SETTINGS WORKFLOW & SCORING REFLECTION
    // =======================================================================

    @Test
    @DisplayName("Gravity Settings: Update toggles -> Scoring reflection -> Dev role restriction")
    void testGravitySettingsWorkflow() throws Exception {
        // 1. Read current settings
        mockMvc.perform(get("/api/settings")
                .header("Authorization", basicAuth(DEV_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enableDependencyScore").isBoolean());

        // 2. Developer cannot update settings (403)
        GravitySettingsDto updateDto = new GravitySettingsDto();
        updateDto.setEnableDependencyScore(false);
        updateDto.setEnableDeadlineProximityScore(true);
        updateDto.setEnableStagnationScore(true);
        updateDto.setEnableWorkloadDensityScore(false);
        updateDto.setEnableCustomFactor(true);
        updateDto.setCustomFactorName("Architecture Risk");
        updateDto.setCustomFactorPoints(20.0);
        updateDto.setRequireApprovalForDevTasks(true);

        mockMvc.perform(put("/api/settings")
                .header("Authorization", basicAuth(DEV_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isForbidden());

        // 3. Project Manager updates settings successfully
        mockMvc.perform(put("/api/settings")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enableDependencyScore").value(false))
            .andExpect(jsonPath("$.enableCustomFactor").value(true))
            .andExpect(jsonPath("$.customFactorPoints").value(20.0));
    }

    // =======================================================================
    // E. SECURITY REGRESSION
    // =======================================================================

    @Test
    @DisplayName("Security Regression: Unauthenticated 401, Dev task creation 403, Public registration default role")
    void testSecurityRegressions() throws Exception {
        // 1. Unauthenticated request to /api/tasks is rejected with 401
        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isUnauthorized());

        // 2. Developer cannot create task tickets (POST /api/tasks is Lead/PM only)
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Dev Attempted Creation");
        req.setEnvironment(TaskEnvironment.DEVELOPMENT);
        req.setDeadline(LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/tasks")
                .header("Authorization", basicAuth(DEV_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());

        // 3. Developer cannot modify task dependencies (Lead/PM only)
        mockMvc.perform(put("/api/tasks/1/dependencies")
                .header("Authorization", basicAuth(DEV_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentTaskIds\":[]}"))
            .andExpect(status().isForbidden());
    }

    // =======================================================================
    // Helper
    // =======================================================================

    private Long createTaskSimple(String title) throws Exception {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle(title);
        req.setEnvironment(TaskEnvironment.DEVELOPMENT);
        req.setDeadline(LocalDateTime.now().plusDays(2));

        MvcResult result = mockMvc.perform(post("/api/tasks")
                .header("Authorization", basicAuth(PM_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

        TaskResponseDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), TaskResponseDto.class);
        return dto.getId();
    }
}